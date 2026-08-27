package com.ble.mcbledevice.handler;

import org.simplejavable.Adapter;
import org.simplejavable.BluetoothUUID;
import org.simplejavable.Characteristic;
import org.simplejavable.Peripheral;
import org.simplejavable.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE 连接处理器（基于 SimpleJavaBLE / SimpleBLE）。
 * 与串口一致：默认不自动连接，需要用户在配置页手动扫描并连接。
 *
 * 设备 UUID（用户约定）：
 *   SERVICE 0xFFF0
 *   WRITE   0xFFF1  主机 -> 硬件（write）
 *   NOTIFY  0xFFF2  硬件 -> 主机（notify）
 */
public class BleHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BleHandler.class);

    private static final String SERVICE_UUID  = "0000fff0-0000-1000-8000-00805f9b34fb";
    private static final String WRITE_UUID    = "0000fff1-0000-1000-8000-00805f9b34fb";
    private static final String NOTIFY_UUID   = "0000fff2-0000-1000-8000-00805f9b34fb";

    private static final BluetoothUUID SERVICE = new BluetoothUUID(SERVICE_UUID);
    private static final BluetoothUUID WRITE   = new BluetoothUUID(WRITE_UUID);
    private static final BluetoothUUID NOTIFY  = new BluetoothUUID(NOTIFY_UUID);

    private static final int SCAN_TIMEOUT_MS = 5000;
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcbledevice-ble");
        t.setDaemon(true);
        return t;
    });

    // 初始化 / 可用性状态
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean bleAvailable = new AtomicBoolean(false);
    private static volatile String adapterInfo = "";
    private static volatile String initError = "";

    // 连接状态
    private static volatile Adapter adapter;
    private static final AtomicReference<Peripheral> currentPeripheral = new AtomicReference<>();
    private static final AtomicBoolean bleEnabled = new AtomicBoolean(false);

    // 扫描状态
    private static final AtomicBoolean scanning = new AtomicBoolean(false);
    private static final List<Peripheral> scanResults = new CopyOnWriteArrayList<>();
    private static volatile String scanStatus = "";

    // 连接后确定的写入方式：false=writeRequest，true=writeCommand
    private static volatile boolean useWriteCommand = false;

    // 最近一次收到的数据（仅用于界面显示）
    private static volatile String lastRxInfo = "";

    /**
     * 初始化 BLE 模块（模组启动时调用）。
     * 只探测适配器，不自动连接、不自动扫描。
     */
    public static void initialize() {
        if (initialized.getAndSet(true)) {
            return;
        }
        executor.submit(BleHandler::initInternal);
    }

    private static void initInternal() {
        try {
            List<Adapter> adapters = Adapter.getAdapters();
            if (adapters.isEmpty()) {
                adapterInfo = "未找到蓝牙适配器";
                LOGGER.warn("[BLE] 未找到蓝牙适配器");
                return;
            }
            adapter = adapters.get(0);
            adapterInfo = adapter.getIdentifier() + " [" + adapter.getAddress() + "]";
            bleAvailable.set(true);
            LOGGER.info("[BLE] 模块初始化完成，适配器: {}", adapterInfo);
        } catch (Throwable t) {
            bleAvailable.set(false);
            initError = t.getMessage() == null ? t.toString() : t.getMessage();
            LOGGER.error("[BLE] 模块初始化失败（可能当前系统不支持 BLE 或 native 库加载失败）", t);
        }
    }

    public static boolean isAvailable() {
        return bleAvailable.get();
    }

    public static String getAdapterInfo() {
        return adapterInfo;
    }

    public static String getInitError() {
        return initError;
    }

    /** 系统蓝牙开关状态（供界面显示） */
    public static String getBluetoothStateText() {
        if (!bleAvailable.get()) {
            return "不可用";
        }
        try {
            return Adapter.isBluetoothEnabled() ? "已开启" : "未开启";
        } catch (Exception e) {
            return "未知";
        }
    }

    /** 用户是否手动开启了 BLE（用于是否自动重连的开关判断） */
    public static boolean isEnabled() {
        return bleEnabled.get();
    }

    public static boolean isConnected() {
        Peripheral p = currentPeripheral.get();
        if (p == null) {
            return false;
        }
        try {
            return p.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isScanning() {
        return scanning.get();
    }

    public static String getScanStatus() {
        return scanStatus;
    }

    public static String getLastRxInfo() {
        return lastRxInfo;
    }

    public static String getCurrentDeviceLabel() {
        Peripheral p = currentPeripheral.get();
        if (p == null) {
            return "";
        }
        return p.getIdentifier() + " [" + p.getAddress() + "]";
    }

    /** 界面用：最近一次扫描结果（不可修改） */
    public static List<Peripheral> getScanResults() {
        return Collections.unmodifiableList(new ArrayList<>(scanResults));
    }

    private static boolean ensureReady() {
        if (!initialized.get()) {
            initialize();
        }
        if (!bleAvailable.get()) {
            scanStatus = "BLE 不可用" + (initError.isEmpty() ? "" : ": " + initError);
            return false;
        }
        return true;
    }

    /**
     * 开始扫描（后台线程，不阻塞渲染/游戏线程）。
     */
    public static void startScan() {
        if (!ensureReady()) {
            return;
        }
        if (scanning.get()) {
            scanStatus = "正在扫描中，请稍候...";
            return;
        }
        scanning.set(true);
        scanResults.clear();
        scanStatus = "正在扫描 " + (SCAN_TIMEOUT_MS / 1000) + " 秒...";
        executor.submit(() -> {
            try {
                adapter.scanFor(SCAN_TIMEOUT_MS);
                List<Peripheral> results = adapter.scanGetResults();
                scanResults.clear();
                scanResults.addAll(results);
                scanStatus = "扫描完成，发现 " + results.size() + " 个设备";
                LOGGER.info("[BLE] 扫描完成，发现 {} 个设备", results.size());
            } catch (Exception e) {
                scanStatus = "扫描失败: " + e.getMessage();
                LOGGER.error("[BLE] 扫描失败", e);
            } finally {
                scanning.set(false);
            }
        });
    }

    /**
     * 停止扫描。
     * 注意：scanFor 是阻塞在后台线程的，scanStop 必须直接调用（不能提交到同一线程池）。
     */
    public static void stopScan() {
        Adapter a = adapter;
        if (a != null && scanning.get()) {
            try {
                a.scanStop();
                scanStatus = "已停止扫描";
            } catch (Exception e) {
                LOGGER.warn("[BLE] 停止扫描异常: {}", e.getMessage());
            }
        } else if (!scanning.get()) {
            scanStatus = "当前没有正在进行的扫描";
        }
    }

    /**
     * 连接扫描结果中第 index 个设备（后台线程）。
     */
    public static void connect(int index) {
        if (!ensureReady()) {
            return;
        }
        if (index < 0 || index >= scanResults.size()) {
            scanStatus = "请先扫描并选择设备";
            return;
        }
        Peripheral target = scanResults.get(index);
        bleEnabled.set(true);
        executor.submit(() -> doConnect(target));
    }

    private static void doConnect(Peripheral target) {
        Peripheral old = currentPeripheral.get();
        if (old != null) {
            try {
                if (old.isConnected()) {
                    try { old.unsubscribe(SERVICE, NOTIFY); } catch (Exception ignored) {}
                    old.disconnect();
                }
            } catch (Exception ignored) {}
        }
        currentPeripheral.set(null);
        useWriteCommand = false;

        try {
            scanStatus = "正在连接 " + target.getIdentifier() + " ...";
            target.connect();

            long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
            while (!target.isConnected() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }

            if (!target.isConnected()) {
                scanStatus = "连接失败: " + target.getIdentifier();
                LOGGER.error("[BLE] 连接失败: {}", target.getIdentifier());
                bleEnabled.set(false);
                return;
            }

            currentPeripheral.set(target);
            target.setEventListener(new Peripheral.EventListener() {
                @Override
                public void onDisconnected() {
                    bleEnabled.set(false);
                    scanStatus = "设备已断开: " + target.getIdentifier();
                    LOGGER.info("[BLE] 设备已断开: {}", target.getIdentifier());
                }
            });

            configureServices(target);
            scanStatus = "已连接: " + target.getIdentifier() + " [" + target.getAddress() + "]";
            LOGGER.info("[BLE] 已连接: {} [{}] MTU={}",
                    target.getIdentifier(), target.getAddress(), target.getMtu());
        } catch (Exception e) {
            bleEnabled.set(false);
            scanStatus = "连接异常: " + e.getMessage();
            LOGGER.error("[BLE] 连接异常", e);
        }
    }

    /**
     * 连接后：发现服务/特征，确定 WRITE(FFF1) 写入方式，并订阅 NOTIFY(FFF2) 通知。
     */
    private static void configureServices(Peripheral p) {
        boolean foundService = false;
        boolean txReady = false;
        boolean rxReady = false;
        try {
            List<Service> services = p.services();
            for (Service s : services) {
                if (!uuidEquals(s.uuid(), SERVICE_UUID)) {
                    continue;
                }
                foundService = true;
                for (Characteristic c : s.characteristics()) {
                    String cuuid = c.uuid();
                    if (uuidEquals(cuuid, WRITE_UUID)) {
                        if (c.canWriteRequest()) {
                            useWriteCommand = false;
                            txReady = true;
                        } else if (c.canWriteCommand()) {
                            useWriteCommand = true;
                            txReady = true;
                        } else {
                            // 部分设备属性探测不全，回退到 writeRequest
                            useWriteCommand = false;
                            txReady = true;
                            LOGGER.warn("[BLE] WRITE FFF1 属性未知，回退 writeRequest");
                        }
                    }
                    if (uuidEquals(cuuid, NOTIFY_UUID)) {
                        if (c.canNotify()) {
                            p.notify(SERVICE, NOTIFY, BleHandler::onRxData);
                            rxReady = true;
                        } else if (c.canIndicate()) {
                            p.indicate(SERVICE, NOTIFY, BleHandler::onRxData);
                            rxReady = true;
                        } else {
                            LOGGER.warn("[BLE] NOTIFY FFF2 不支持通知/指示，无法接收数据");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[BLE] 服务/特征配置失败", e);
            return;
        }

        if (!foundService) {
            LOGGER.warn("[BLE] 未找到服务 FFF0，设备可能不兼容（仍会尝试收发）");
        }
        if (!txReady) {
            LOGGER.warn("[BLE] WRITE FFF1 未就绪，发送可能失败");
        }
        if (!rxReady) {
            LOGGER.warn("[BLE] NOTIFY FFF2 未就绪");
        }
    }

    /**
     * 手动断开 BLE。
     */
    public static void disconnect() {
        bleEnabled.set(false);
        Peripheral p = currentPeripheral.get();
        if (p == null) {
            scanStatus = "当前未连接设备";
            return;
        }
        executor.submit(() -> {
            try {
                if (p.isConnected()) {
                    try { p.unsubscribe(SERVICE, NOTIFY); } catch (Exception ignored) {}
                    p.disconnect();
                }
            } catch (Exception e) {
                LOGGER.error("[BLE] 断开异常", e);
            }
            currentPeripheral.set(null);
            scanStatus = "已断开";
            LOGGER.info("[BLE] 已手动断开");
        });
    }

    /**
     * 发送二进制数据包（仅 BLE 已启用且已连接时发送；与串口一致，未开启不自动重连）。
     */
    public static void sendData(byte[] packet) {
        if (packet == null || packet.length == 0) {
            return;
        }
        if (!bleEnabled.get() || !isConnected()) {
            return;
        }
        Peripheral p = currentPeripheral.get();
        if (p == null) {
            return;
        }
        executor.submit(() -> {
            try {
                if (useWriteCommand) {
                    p.writeCommand(SERVICE, WRITE, packet);
                } else {
                    p.writeRequest(SERVICE, WRITE, packet);
                }
                LOGGER.debug("[BLE] 已发送 {} 字节 -> FFF1", packet.length);
            } catch (Exception e) {
                LOGGER.error("[BLE] 发送失败（包长 {}，MTU {}）", packet.length, p.getMtu(), e);
            }
        });
    }

    /**
     * NOTIFY 通知回调（硬件 -> 主机）。
     * 目前仅记录日志与界面状态，后续可扩展为协议解析。
     */
    private static void onRxData(byte[] data) {
        if (data == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        lastRxInfo = "RX(" + data.length + "B): " + sb.toString().trim();
        LOGGER.debug("[BLE] {}", lastRxInfo);
    }

    /**
     * UUID 比较：兼容 16 位短格式与标准 128 位格式。
     */
    private static boolean uuidEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String na = normalizeUuid(a);
        String nb = normalizeUuid(b);
        return na.equals(nb);
    }

    private static String normalizeUuid(String uuid) {
        String s = uuid.trim().toLowerCase(Locale.ROOT).replace("-", "");
        if (s.length() == 32) {
            // 标准蓝牙基础 UUID: 0000xxxx-0000-1000-8000-00805f9b34fb
            String suffix = s.substring(4);
            if (suffix.equals("00001000800000805f9b34fb")) {
                return s.substring(0, 4);
            }
        }
        return s;
    }

    /**
     * 释放资源（模组卸载/游戏关闭时调用）。
     * 同步断开连接（不依赖队列执行），随后关闭线程池。
     */
    public static void shutdown() {
        bleEnabled.set(false);
        Peripheral p = currentPeripheral.get();
        if (p != null) {
            try {
                if (p.isConnected()) {
                    try {
                        p.unsubscribe(SERVICE, NOTIFY);
                    } catch (Exception ignored) {
                    }
                    p.disconnect();
                    LOGGER.info("[BLE] 已断开连接");
                }
            } catch (Exception e) {
                LOGGER.error("[BLE] 关闭时断开异常", e);
            }
            currentPeripheral.set(null);
        }
        executor.shutdownNow();
    }
}
