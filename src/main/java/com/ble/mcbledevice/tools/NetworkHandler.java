package com.ble.mcbledevice.tools;

import com.ble.mcbledevice.config.ConfigManager;
import com.ble.mcbledevice.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络通信处理器。
 * 负责 UDP 广播发现设备，并维护 TCP 连接发送数据。
 *
 * 线程模型：所有阻塞操作（连接、重连、发送）只在单线程 executor 内执行，
 * 游戏线程调用 sendData 只负责入队，绝不阻塞。
 */
public class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);

    // UDP 广播端口（与硬件约定）
    private static final int DISCOVER_PORT = 8888;
    // 广播超时（毫秒）
    private static final int DISCOVER_TIMEOUT = 3000;
    // 重连冷却（毫秒）
    private static final long RECONNECT_COOLDOWN_MS = 5000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcbledevice-network");
        t.setDaemon(true);
        return t;
    });

    // 以下字段会被游戏线程读取，写入集中在 executor，用 volatile 保证可见性
    private static volatile Socket tcpSocket;
    private static volatile String deviceIp;
    private static volatile int devicePort;
    private static volatile String discoveryStatus = "";

    // 以下状态只在 executor 线程内访问
    private static long lastReconnectAttempt = 0;
    private static boolean lastConnectionState = false;

    /**
     * 初始化网络模块（不自动连接，需手动触发）。
     */
    public static void initialize() {
        tcpSocket = null;
        ModConfig config = ConfigManager.getConfig();
        if (config != null) {
            LOGGER.info("网络模块初始化，当前配置设备 {}:{}", config.deviceIp, config.devicePort);
        } else {
            LOGGER.warn("网络模块初始化，但配置为空");
        }
    }

    public static boolean isConnected() {
        Socket socket = tcpSocket;
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public static String getDeviceIp() {
        return deviceIp == null ? "" : deviceIp;
    }

    public static int getDevicePort() {
        return devicePort;
    }

    public static String getDiscoveryStatus() {
        return discoveryStatus;
    }

    /**
     * 连接设备（后台线程）。根据配置选择手动直连或自动发现。
     */
    public static void connect() {
        executor.submit(() -> {
            ModConfig config = ConfigManager.getConfig();
            if (config == null) {
                LOGGER.error("配置未加载，无法连接");
                return;
            }
            if (config.connectionMode == ModConfig.ConnectionMode.MANUAL) {
                if (config.deviceIp == null || config.deviceIp.isEmpty()) {
                    LOGGER.warn("未配置设备IP");
                    return;
                }
                deviceIp = config.deviceIp;
                devicePort = config.devicePort;
                LOGGER.info("手动连接 {}:{}", deviceIp, devicePort);
                doConnect(deviceIp, devicePort);
            } else {
                LOGGER.info("开始自动发现设备");
                doDiscover();
            }
        });
    }

    /**
     * UDP 广播发现设备。
     * 不使用 255.255.255.255（会在虚拟网卡等所有接口上以奇怪地址发送），
     * 而是枚举本机每个已启用的 IPv4 网卡，向各自的定向广播地址发送，
     * 这样 192.168.1.x、192.168.2.x 等不同子网的设备都能收到。
     */
    public static void discoverDevice() {
        executor.submit(NetworkHandler::doDiscover);
    }

    private static void doDiscover() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            byte[] sendData = "MC_DISCOVER".getBytes();
            int sent = sendDiscoveryBroadcasts(socket, sendData);
            if (sent == 0) {
                discoveryStatus = "未找到可用的 IPv4 网卡进行广播";
                LOGGER.warn("未找到可用的 IPv4 网卡进行广播");
                return;
            }
            discoveryStatus = "已向 " + sent + " 个网卡广播发现包，等待设备回应...";
            LOGGER.info("已向 {} 个网卡广播发现包", sent);

            // 接收回复
            socket.setSoTimeout(DISCOVER_TIMEOUT);
            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(recvPacket);
            String reply = new String(recvPacket.getData(), 0, recvPacket.getLength());
            LOGGER.info("收到回复: {}", reply);

            // 解析 "MC_DEVICE|192.168.1.200|12345"
            if (reply.startsWith("MC_DEVICE|")) {
                String[] parts = reply.split("\\|");
                if (parts.length == 3) {
                    deviceIp = parts[1];
                    devicePort = Integer.parseInt(parts[2]);
                    discoveryStatus = "发现设备 " + deviceIp + ":" + devicePort;
                    LOGGER.info("发现设备: {}:{}", deviceIp, devicePort);
                    saveDeviceToConfig(deviceIp, devicePort);
                    doConnect(deviceIp, devicePort);
                }
            } else {
                discoveryStatus = "收到未知回复: " + reply;
                LOGGER.warn("未知回复格式: {}", reply);
            }
        } catch (SocketTimeoutException e) {
            discoveryStatus = "未发现设备（超时）";
            LOGGER.warn("设备发现超时，未找到设备");
        } catch (Exception e) {
            discoveryStatus = "扫描失败: " + e.getMessage();
            LOGGER.error("设备发现异常", e);
        }
    }

    /**
     * 向本机所有已启用 IPv4 网卡的定向广播地址发送发现包。
     * @return 成功发送的网卡数
     */
    private static int sendDiscoveryBroadcasts(DatagramSocket socket, byte[] payload) throws SocketException {
        int sent = 0;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            try {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress ip = ia.getAddress();
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast == null || !(ip instanceof Inet4Address) || !(broadcast instanceof Inet4Address)) {
                        continue;
                    }
                    DatagramPacket packet = new DatagramPacket(payload, payload.length, broadcast, DISCOVER_PORT);
                    socket.send(packet);
                    sent++;
                    LOGGER.debug("已向 {} 发送发现包（网卡 {}）", broadcast, ni.getName());
                }
            } catch (IOException e) {
                LOGGER.warn("网卡 {} 广播失败: {}", ni.getName(), e.getMessage());
            }
        }
        return sent;
    }

    /**
     * 连接 TCP 服务器（后台线程）。
     */
    public static void connectToDevice(String ip, int port) {
        executor.submit(() -> doConnect(ip, port));
    }

    private static void doConnect(String ip, int port) {
        disconnect();
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 3000);
            socket.setSoTimeout(5000); // 读取超时（用于心跳或接收）
            tcpSocket = socket;
            deviceIp = ip;
            devicePort = port;
            lastConnectionState = true;
            LOGGER.info("TCP 连接成功: {}:{}", ip, port);
        } catch (Exception e) {
            tcpSocket = null;
            lastConnectionState = false;
            LOGGER.error("TCP 连接失败: {}:{}", ip, port, e);
        }
    }

    /**
     * 发送二进制数据。
     * 连接检查与（可能的）重连都在 executor 线程内完成，游戏线程不会阻塞。
     */
    public static void sendData(byte[] packet) {
        if (packet == null || packet.length == 0) {
            return;
        }
        executor.submit(() -> {
            if (!ensureConnected()) {
                if (lastConnectionState) {
                    LOGGER.warn("网络未连接，数据包已丢弃");
                    lastConnectionState = false;
                }
                return;
            }
            if (!lastConnectionState) {
                LOGGER.info("网络连接已恢复");
                lastConnectionState = true;
            }
            try {
                OutputStream os = tcpSocket.getOutputStream();
                os.write(packet);
                os.flush();
            } catch (IOException e) {
                LOGGER.error("发送数据失败", e);
                try {
                    if (tcpSocket != null) {
                        tcpSocket.close();
                    }
                } catch (IOException ignored) {
                }
                tcpSocket = null;
                lastConnectionState = false;
            }
        });
    }

    /**
     * 确保 TCP 连接有效，无效则尝试重连。
     * 只能在 executor 线程内调用。
     */
    private static boolean ensureConnected() {
        if (tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed()) {
            return true;
        }
        if (deviceIp == null || devicePort == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastReconnectAttempt < RECONNECT_COOLDOWN_MS) {
            return false;
        }
        lastReconnectAttempt = now;

        LOGGER.info("尝试重连至 {}:{}", deviceIp, devicePort);
        try {
            if (tcpSocket != null) {
                try {
                    tcpSocket.close();
                } catch (IOException ignored) {
                }
                tcpSocket = null;
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(deviceIp, devicePort), 3000);
            socket.setSoTimeout(5000);
            tcpSocket = socket;
            LOGGER.info("重连成功");
            return true;
        } catch (Exception e) {
            LOGGER.error("重连失败", e);
            return false;
        }
    }

    /**
     * 断开连接（关闭套接字，动作本身很快，可在任意线程调用）。
     */
    public static void disconnect() {
        Socket socket = tcpSocket;
        tcpSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 保存设备信息到配置。
     */
    private static void saveDeviceToConfig(String ip, int port) {
        ModConfig config = ConfigManager.getConfig();
        if (config != null) {
            config.deviceIp = ip;
            config.devicePort = port;
            // 发现成功后自动切换到手动模式，下次启动直接用
            config.connectionMode = ModConfig.ConnectionMode.MANUAL;
            ConfigManager.save();
            LOGGER.info("设备信息已保存，下次启动将自动连接");
        }
    }

    /**
     * 关闭线程池（服务器停止时调用）。
     */
    public static void shutdown() {
        disconnect();
        executor.shutdownNow();
    }
}
