package com.ble.mcbledevice.handler;
import com.fazecast.jSerialComm.SerialPort;
import com.ble.mcbledevice.tools.PacketPacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.ble.mcbledevice.config.ConfigManager;
import com.ble.mcbledevice.config.ModConfig;


public class SerialHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialHandler.class);

    // 移除硬编码常量，改为从配置读取
    private static volatile SerialPort serialPort;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcbledevice-serial");
        t.setDaemon(true);
        return t;
    });

    // 重连冷却控制
    private static long lastReconnectAttempt = 0;
    private static final long RECONNECT_COOLDOWN_MS = 5000; // 5秒内不重复尝试

    // 是否已手动启用串口（未启用时不会自动打开/重连）
    private static volatile boolean serialEnabled = false;
    // 连接状态变化日志抑制
    private static boolean lastSerialState = false;

    /**
     * 初始化串口连接（模组启动时调用）
     * 仅初始化模块，不自动连接；需要手动点击“连接串口”
     */
    public static void initialize() {
        ModConfig config = ConfigManager.getConfig();
        if (config != null) {
            LOGGER.info(
                    "🔌 串口模块初始化，配置 {} @{}（未自动连接）",
                    config.serialPort,
                    config.baudRate
            );
        }
    }

    /**
     * 手动连接串口（启用后发送数据时支持自动重连）
     */
    public static void connect() {
        serialEnabled = true;
        executor.submit(SerialHandler::ensureConnected);
    }

    /**
     * 手动断开串口
     */
    public static void disconnect() {
        serialEnabled = false;
        executor.submit(() -> {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
                LOGGER.info("🔌 串口已断开");
            }
            serialPort = null;
        });
    }

    public static boolean isEnabled() {
        return serialEnabled;
    }

    public static boolean isConnected() {
        return serialPort != null && serialPort.isOpen();
    }

    /**
     * 确保串口连接可用（支持重连）
     * @return true 如果连接可用，否则 false
     */
    private static synchronized boolean ensureConnected() {
        // 未手动启用串口时，不自动连接
        if (!serialEnabled) {
            return false;
        }
        // 如果端口已打开，直接返回 true
        if (serialPort != null && serialPort.isOpen()) {
            return true;
        }

        // 防止频繁重连（冷却期）
        long now = System.currentTimeMillis();
        if (now - lastReconnectAttempt < RECONNECT_COOLDOWN_MS) {
            return false;
        }
        lastReconnectAttempt = now;

        // 从配置获取最新端口和波特率
        ModConfig config = ConfigManager.getConfig();
        if (config == null) {
            LOGGER.error("❌ 配置未加载，无法连接串口");
            return false;
        }

        String portName = config.serialPort;
        int baudRate = config.baudRate;

        // 关闭旧端口（如果有）
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            serialPort = null;
        }

        // 尝试打开新端口
        LOGGER.info("🔄 尝试连接串口 {} 波特率 {}", portName, baudRate);
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(1);
            serialPort.setParity(SerialPort.NO_PARITY);

            if (serialPort.openPort()) {
                LOGGER.info("✅ 串口 {} 连接成功！", portName);
                return true;
            } else {
                LOGGER.error("❌ 串口 {} 打开失败，请检查硬件连接或端口号。", portName);
                serialPort = null;
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("❌ 串口连接异常: {}", e.getMessage());
            serialPort = null;
            return false;
        }
    }

    /**
     * 发送字符串数据（已废弃，但保留兼容）
     * @param data 字符串数据
     */
    @Deprecated
    public static void sendData(String data) {
        if (data == null || data.isEmpty()) return;
        // 转为字节数组发送（不带换行，如需换行请自行添加）
        sendData(data.getBytes());
    }

    /**
     * 发送二进制数据包（支持自动重连）
     * @param packet 数据包字节数组
     */
    public static void sendData(byte[] packet) {
        if (packet == null || packet.length == 0) return;

        // 连接检查与重连全部在后台线程完成，游戏线程不会阻塞
        executor.submit(() -> {
            if (!ensureConnected()) {
                if (lastSerialState) {
                    LOGGER.warn("串口未连接，数据包已丢弃");
                    lastSerialState = false;
                }
                return;
            }
            if (!lastSerialState) {
                LOGGER.info("串口连接已恢复");
                lastSerialState = true;
            }
            try {
                serialPort.writeBytes(packet, packet.length);
            } catch (Exception e) {
                LOGGER.error("❌ 发送数据包失败: {}", e.getMessage());
                // 发送失败，标记端口为无效，下次 sendData 会尝试重连
                if (serialPort != null && !serialPort.isOpen()) {
                    serialPort = null;
                }
            }
        });
    }

    /**
     * 关闭串口资源（模组卸载或游戏关闭时调用）
     */
    /**
     * 批量投递：整批帧一次入队，由本通道按帧边界分块后顺序写出（不拆分数据段）。
     */
    public static void sendFrames(List<byte[]> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        List<byte[]> snapshot = new ArrayList<>(frames);
        executor.submit(() -> {
            if (!ensureConnected()) {
                if (lastSerialState) {
                    LOGGER.warn("串口未连接，批次已丢弃");
                    lastSerialState = false;
                }
                return;
            }
            if (!lastSerialState) {
                LOGGER.info("串口连接已恢复");
                lastSerialState = true;
            }
            for (PacketPacker.Chunk chunk : PacketPacker.pack(snapshot, PacketPacker.DEFAULT_MAX_CHUNK)) {
                try {
                    serialPort.writeBytes(chunk.data, chunk.data.length);
                } catch (Exception e) {
                    LOGGER.error("发送数据块失败（{} 帧 / {} 字节）: {}",
                            chunk.frameCount, chunk.data.length, e.getMessage());
                    if (serialPort != null && !serialPort.isOpen()) {
                        serialPort = null;
                    }
                    return;
                }
            }
        });
    }

    public static void shutdown() {
        executor.shutdownNow();
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            LOGGER.info("🔌 串口已关闭");
        }
    }
}
