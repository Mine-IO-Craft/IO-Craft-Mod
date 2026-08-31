package com.ble.mcbledevice.client.screen;

import com.ble.mcbledevice.McBleDevice;
import com.ble.mcbledevice.config.ConfigManager;
import com.ble.mcbledevice.config.ModConfig;
import com.ble.mcbledevice.handler.BleHandler;
import com.ble.mcbledevice.handler.SerialHandler;
import com.ble.mcbledevice.tools.NetworkHandler;
import com.fazecast.jSerialComm.SerialPort;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.simplejavable.Peripheral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 自定义设置页（按 P 键打开）：
 * 不依赖 Cloth Config 的界面。整个面板可滚动：面板高度永远适配窗口，
 * 内容超高时右侧出现滚动条，滚轮上下滑动；BLE 区块固定放在最下面。
 */
public class McBleDeviceConfigScreen extends Screen {

    private static final int PANEL_W = 500;

    // ---- 内容布局（相对面板顶部） ----
    private static final int[] FIELD_Y = {16, 36, 56, 76, 96};     // 5 个输入框
    private static final int[] TOGGLE_Y = {16, 36, 56, 76};        // 8 个发送开关（两列）
    private static final int STATUS_START = 126;                   // 状态区第一行
    private static final int STATUS_STEP = 16;
    private static final int ACTION_Y = 242;                       // TCP/串口操作按钮行
    private static final int BLE_BTN_Y = 288;                      // BLE 操作按钮行
    private static final int DEVICE_TOP = 312;                     // BLE 设备列表第一行
    private static final int DEVICE_STEP = 14;

    private TextFieldWidget deviceIpField;
    private TextFieldWidget devicePortField;
    private TextFieldWidget serialPortField;
    private TextFieldWidget baudRateField;
    private TextFieldWidget sendIntervalField;

    private ButtonWidget bleSelectButton;
    private int bleSelectedIndex = 0;

    // 所有需要随滚动移动的控件
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private final List<Integer> widgetBaseY = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int viewportH;
    private int contentH;
    private int scrollY = 0;
    private int lastDeviceCount = -1;

    private String availablePorts = "无";
    private String saveMessage = "";
    private int saveMessageTicks = 0;

    public McBleDeviceConfigScreen() {
        super(Text.literal("IO-Craft 设置"));
    }

    private int deviceCount() {
        return BleHandler.getScanResults().size();
    }

    private int computeContentH() {
        return DEVICE_TOP + deviceCount() * DEVICE_STEP + 16;
    }

    private void register(ClickableWidget widget, int baseY) {
        widgets.add(widget);
        widgetBaseY.add(baseY);
        addDrawableChild(widget);
    }

    private void applyScroll() {
        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).setY(panelY + widgetBaseY.get(i) - scrollY);
        }
    }

    private void updateLayout() {
        contentH = computeContentH();
        viewportH = Math.max(100, Math.min(contentH, Math.max(100, height - 20)));
        int maxScroll = Math.max(0, contentH - viewportH);
        if (scrollY > maxScroll) {
            scrollY = maxScroll;
        }
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(0, (height - viewportH) / 2);
        applyScroll();
    }

    @Override
    protected void init() {
        ModConfig config = ConfigManager.getConfig();
        if (config == null) {
            saveMessage = "配置未加载";
            addDrawableChild(
                    ButtonWidget.builder(Text.literal("关闭"), b -> close())
                            .dimensions(width / 2 - 40, height / 2 - 10, 80, 20)
                            .build()
            );
            return;
        }

        refreshAvailablePorts();
        bleSelectedIndex = 0;
        lastDeviceCount = deviceCount();
        widgets.clear();
        widgetBaseY.clear();

        updateLayout();

        // ---- 网络 / 串口配置字段 ----
        deviceIpField = new TextFieldWidget(textRenderer, panelX + 90, 0, 140, 20, Text.literal("设备IP"));
        deviceIpField.setText(config.deviceIp);
        register(deviceIpField, FIELD_Y[0]);

        devicePortField = new TextFieldWidget(textRenderer, panelX + 90, 0, 140, 20, Text.literal("TCP端口"));
        devicePortField.setText(String.valueOf(config.devicePort));
        register(devicePortField, FIELD_Y[1]);

        serialPortField = new TextFieldWidget(textRenderer, panelX + 90, 0, 140, 20, Text.literal("串口"));
        serialPortField.setText(config.serialPort);
        register(serialPortField, FIELD_Y[2]);

        baudRateField = new TextFieldWidget(textRenderer, panelX + 90, 0, 140, 20, Text.literal("波特率"));
        baudRateField.setText(String.valueOf(config.baudRate));
        register(baudRateField, FIELD_Y[3]);

        sendIntervalField = new TextFieldWidget(textRenderer, panelX + 90, 0, 140, 20, Text.literal("发送间隔(ms)"));
        sendIntervalField.setText(String.valueOf(config.sendIntervalMs));
        register(sendIntervalField, FIELD_Y[4]);

        // ---- 发送内容开关 ----
        register(createToggle(panelX + 260, 0, "生命值", () -> config.send.health, v -> config.send.health = v), TOGGLE_Y[0]);
        register(createToggle(panelX + 365, 0, "饥饿值", () -> config.send.food, v -> config.send.food = v), TOGGLE_Y[0]);
        register(createToggle(panelX + 260, 0, "饱和度", () -> config.send.saturation, v -> config.send.saturation = v), TOGGLE_Y[1]);
        register(createToggle(panelX + 365, 0, "坐标", () -> config.send.position, v -> config.send.position = v), TOGGLE_Y[1]);
        register(createToggle(panelX + 260, 0, "等级", () -> config.send.level, v -> config.send.level = v), TOGGLE_Y[2]);
        register(createToggle(panelX + 365, 0, "难度", () -> config.send.difficulty, v -> config.send.difficulty = v), TOGGLE_Y[2]);
        register(createToggle(panelX + 260, 0, "时间", () -> config.send.time, v -> config.send.time = v), TOGGLE_Y[3]);
        register(createToggle(panelX + 365, 0, "速度", () -> config.send.speed, v -> config.send.speed = v), TOGGLE_Y[3]);

        // ---- TCP / 串口 操作按钮 ----
        register(ButtonWidget.builder(Text.literal("连接TCP"), b -> {
                    applyFields();
                    NetworkHandler.connect();
                    showMessage("正在连接 TCP...");
                }).dimensions(panelX + 10, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("断开TCP"), b -> {
                    NetworkHandler.disconnect();
                    showMessage("TCP 已断开");
                }).dimensions(panelX + 74, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("连接串口"), b -> {
                    applyFields();
                    SerialHandler.connect();
                    refreshAvailablePorts();
                    showMessage("正在连接串口...");
                }).dimensions(panelX + 138, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("断开串口"), b -> {
                    SerialHandler.disconnect();
                    showMessage("串口已断开");
                }).dimensions(panelX + 202, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("扫描设备"), b -> {
                    NetworkHandler.discoverDevice();
                    showMessage("正在扫描设备...");
                }).dimensions(panelX + 266, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("保存"), b -> save())
                .dimensions(panelX + 330, 0, 60, 18).build(), ACTION_Y);
        register(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(panelX + 394, 0, 60, 18).build(), ACTION_Y);

        // ---- BLE 操作按钮 ----
        register(ButtonWidget.builder(Text.literal("扫描BLE"), b -> {
                    BleHandler.startScan();
                    showMessage("正在扫描 BLE 设备...");
                }).dimensions(panelX + 10, 0, 64, 18).build(), BLE_BTN_Y);
        register(ButtonWidget.builder(Text.literal("停止"), b -> {
                    BleHandler.stopScan();
                    showMessage("已请求停止扫描");
                }).dimensions(panelX + 78, 0, 44, 18).build(), BLE_BTN_Y);
        bleSelectButton = ButtonWidget.builder(Text.literal("选择设备"), b -> {
                    int count = BleHandler.getScanResults().size();
                    if (count == 0) {
                        showMessage("请先扫描 BLE 设备");
                        return;
                    }
                    bleSelectedIndex = (bleSelectedIndex + 1) % count;
                })
                .dimensions(panelX + 126, 0, 100, 18)
                .build();
        register(bleSelectButton, BLE_BTN_Y);
        register(ButtonWidget.builder(Text.literal("连接BLE"), b -> {
                    BleHandler.connect(bleSelectedIndex);
                    showMessage("正在连接 BLE 设备...");
                }).dimensions(panelX + 230, 0, 64, 18).build(), BLE_BTN_Y);
        register(ButtonWidget.builder(Text.literal("断开BLE"), b -> {
                    BleHandler.disconnect();
                    showMessage("BLE 已断开");
                }).dimensions(panelX + 298, 0, 64, 18).build(), BLE_BTN_Y);

        applyScroll();
    }

    private ButtonWidget createToggle(
            int x,
            int y,
            String name,
            BooleanSupplier getter,
            Consumer<Boolean> setter
    ) {
        return ButtonWidget.builder(
                        Text.literal(name + ": " + (getter.getAsBoolean() ? "开" : "关")),
                        b -> {
                            setter.accept(!getter.getAsBoolean());
                            b.setMessage(Text.literal(name + ": " + (getter.getAsBoolean() ? "开" : "关")));
                        }
                )
                .dimensions(x, y, 85, 18)
                .build();
    }

    private void showMessage(String message) {
        saveMessage = message;
        saveMessageTicks = 60;
    }

    private void refreshAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            availablePorts = "无";
            return;
        }
        String joined = Arrays.stream(ports)
                .map(SerialPort::getSystemPortName)
                .collect(Collectors.joining(", "));
        if (joined.length() > 42) {
            joined = joined.substring(0, 42) + "...";
        }
        availablePorts = joined;
    }

    /**
     * 把输入框内容写入内存中的配置；返回是否成功
     */
    private boolean applyFields() {
        ModConfig config = ConfigManager.getConfig();
        if (config == null) {
            showMessage("配置未加载");
            return false;
        }
        try {
            config.deviceIp = deviceIpField.getText().trim();
            config.devicePort = Integer.parseInt(devicePortField.getText().trim());
            config.serialPort = serialPortField.getText().trim();
            config.baudRate = Integer.parseInt(baudRateField.getText().trim());
            config.sendIntervalMs = Integer.parseInt(sendIntervalField.getText().trim());
            if (config.sendIntervalMs < 100) {
                config.sendIntervalMs = 100;
            }
            return true;
        } catch (Exception e) {
            McBleDevice.LOGGER.warn("配置输入有误: {}", e.getMessage());
            showMessage("输入有误，请检查 IP/端口/波特率/间隔");
            return false;
        }
    }

    private void save() {
        if (applyFields()) {
            ConfigManager.save();
            showMessage("已保存配置");
        }
    }

    @Override
    public void tick() {
        if (saveMessageTicks > 0) {
            saveMessageTicks--;
            if (saveMessageTicks == 0) {
                saveMessage = "";
            }
        }
        if (bleSelectButton != null) {
            int count = BleHandler.getScanResults().size();
            if (count == 0) {
                bleSelectedIndex = 0;
                bleSelectButton.setMessage(Text.literal("选择设备 -"));
            } else {
                if (bleSelectedIndex >= count) {
                    bleSelectedIndex = count - 1;
                }
                bleSelectButton.setMessage(Text.literal("选择设备 " + (bleSelectedIndex + 1) + "/" + count));
            }
        }
        int count = deviceCount();
        if (count != lastDeviceCount) {
            lastDeviceCount = count;
            updateLayout();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentH - viewportH);
        if (maxScroll > 0) {
            int delta = (int) Math.round(verticalAmount * 14);
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - delta));
            applyScroll();
            setFocused(null);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 全屏暗色背景
        context.fill(0, 0, width, height, 0x99000000);

        if (deviceIpField == null) {
            context.drawText(textRenderer, saveMessage, width / 2 - 40, height / 2 - 4, 0xFFFFFF, false);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // 面板（高度 = 视口高度，永远不超出屏幕）
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + viewportH, 0xFF333333);

        int top = panelY - scrollY;

        // ---- 区块标题与分隔线 ----
        context.drawText(textRenderer, "网络 / 串口配置", panelX + 10, top + 4, 0xFFD7A0, false);
        context.fill(panelX + 8, top + 104, panelX + PANEL_W - 8, top + 105, 0xFF555555);
        context.drawText(textRenderer, "连接状态", panelX + 10, top + 110, 0xFFD7A0, false);
        context.fill(panelX + 8, top + 220, panelX + PANEL_W - 8, top + 221, 0xFF555555);
        context.drawText(textRenderer, "连接操作", panelX + 10, top + 226, 0xFFD7A0, false);
        context.fill(panelX + 8, top + 264, panelX + PANEL_W - 8, top + 265, 0xFF555555);
        context.drawText(textRenderer, "BLE 设备", panelX + 10, top + 270, 0xFFD7A0, false);

        // ---- 字段标签 ----
        context.drawText(textRenderer, "设备IP", panelX + 10, top + 19, 0xFFFFFF, false);
        context.drawText(textRenderer, "TCP端口", panelX + 10, top + 39, 0xFFFFFF, false);
        context.drawText(textRenderer, "串口", panelX + 10, top + 59, 0xFFFFFF, false);
        context.drawText(textRenderer, "波特率", panelX + 10, top + 79, 0xFFFFFF, false);
        context.drawText(textRenderer, "发送间隔(ms)", panelX + 10, top + 99, 0xFFFFFF, false);
        context.drawText(textRenderer, "发送内容", panelX + 260, top + 4, 0xFFD7A0, false);

        drawStatus(context, panelX, top);

        // ---- BLE 设备列表 ----
        List<Peripheral> devices = BleHandler.getScanResults();
        for (int i = 0; i < devices.size(); i++) {
            Peripheral p = devices.get(i);
            String name = p.getIdentifier() == null || p.getIdentifier().isEmpty() ? "未知设备" : p.getIdentifier();
            String line = String.format("[%d] %s (%s) %ddBm", i + 1, name, p.getAddress(), p.getRssi());
            int color = (i == bleSelectedIndex) ? 0xFFFFD700 : 0xFFFFFF;
            context.drawText(textRenderer, line, panelX + 10, top + DEVICE_TOP + i * DEVICE_STEP, color, false);
        }

        // ---- 提示消息 ----
        if (!saveMessage.isEmpty()) {
            context.drawText(textRenderer, saveMessage, panelX + 10, top + DEVICE_TOP + devices.size() * DEVICE_STEP + 2, 0xFFD7A0, false);
        }

        // ---- 滚动条 ----
        int maxScroll = Math.max(0, contentH - viewportH);
        if (maxScroll > 0) {
            int trackX = panelX + PANEL_W - 5;
            int trackTop = panelY + 2;
            int trackH = viewportH - 4;
            context.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0xFF555555);
            int thumbH = Math.max(24, trackH * viewportH / contentH);
            int thumbY = trackTop + (trackH - thumbH) * scrollY / maxScroll;
            context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 空实现：由 render() 自行绘制背景，避免 super.render 再次覆盖面板
    }

    private void drawStatus(DrawContext context, int x, int top) {
        ModConfig config = ConfigManager.getConfig();

        String tcpStatus;
        if (NetworkHandler.isConnected()) {
            tcpStatus = "TCP: 已连接 " + NetworkHandler.getDeviceIp() + ":" + NetworkHandler.getDevicePort();
        } else if (config != null && config.deviceIp != null && !config.deviceIp.isEmpty()) {
            tcpStatus = "TCP: 未连接（目标 " + config.deviceIp + ":" + config.devicePort + "）";
        } else {
            tcpStatus = "TCP: 未连接（未配置设备）";
        }
        context.drawText(textRenderer, tcpStatus, x + 10, top + STATUS_START, 0xFFFFFF, false);

        String serialStatus;
        if (SerialHandler.isConnected()) {
            serialStatus = "串口: 已连接 " + (config == null ? "" : config.serialPort)
                    + " @" + (config == null ? "" : String.valueOf(config.baudRate));
        } else if (SerialHandler.isEnabled()) {
            serialStatus = "串口: 未连接（已启用，自动重连中）";
        } else {
            serialStatus = "串口: 未连接（未启用）";
        }
        context.drawText(textRenderer, serialStatus + "  可用: " + availablePorts,
                x + 10, top + STATUS_START + STATUS_STEP, 0xFFFFFF, false);

        String discovery = NetworkHandler.getDiscoveryStatus();
        String discoveryText = discovery == null || discovery.isEmpty() ? "设备发现: 未扫描" : "设备发现: " + discovery;
        context.drawText(textRenderer, discoveryText, x + 10, top + STATUS_START + STATUS_STEP * 2, 0xFFFFFF, false);

        String bleStatus;
        if (!BleHandler.isAvailable()) {
            String err = BleHandler.getInitError();
            bleStatus = "BLE: 不可用" + (err.isEmpty() ? "" : "（" + err + "）");
        } else {
            bleStatus = "BLE: " + BleHandler.getAdapterInfo() + " | 蓝牙" + BleHandler.getBluetoothStateText();
        }
        context.drawText(textRenderer, bleStatus, x + 10, top + STATUS_START + STATUS_STEP * 3, 0xFFFFFF, false);

        String bleConn;
        if (BleHandler.isConnected()) {
            bleConn = "BLE连接: 已连接 " + BleHandler.getCurrentDeviceLabel();
        } else if (BleHandler.isEnabled()) {
            bleConn = "BLE连接: 未连接（已启用）";
        } else {
            bleConn = "BLE连接: 未连接（未启用）";
        }
        context.drawText(textRenderer, bleConn, x + 10, top + STATUS_START + STATUS_STEP * 4, 0xFFFFFF, false);

        String scanInfo = BleHandler.getScanStatus();
        context.drawText(textRenderer, "BLE扫描: " + (scanInfo.isEmpty() ? "未扫描" : scanInfo),
                x + 10, top + STATUS_START + STATUS_STEP * 5, 0xFFFFFF, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
