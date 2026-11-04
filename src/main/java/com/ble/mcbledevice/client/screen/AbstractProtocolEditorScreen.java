package com.ble.mcbledevice.client.screen;

import com.ble.mcbledevice.client.screen.protocol.ProtocolRegistry;
import com.ble.mcbledevice.menu.SenderEditorData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 协议编辑器的通用逻辑（方块发送器与 ESP32-S3 手持物品共用）。
 *
 * 数据来源通过 SenderEditorData 接口访问，底层始终是 module + cmd + len + data 的原始协议帧。
 * 新页面数值输入使用十进制；未知协议或长度不符时切换为原始数据编辑，避免数据丢失。
 *
 * 子类负责：
 *   1. 底部按钮（保存 / 经典页面 / 发送等）；
 *   2. onSave / onSend 的实际网络发送目标。
 */
public abstract class AbstractProtocolEditorScreen<T extends ScreenHandler & SenderEditorData>
        extends HandledScreen<T> {

    protected static final int PANEL_W = 320;
    protected static final int PANEL_H = 280;

    private static final int GRID_COLS = 3;
    private static final int GRID_BTN_W = 98;
    private static final int GRID_BTN_H = 18;
    private static final int GRID_GAP_X = 4;
    private static final int GRID_GAP_Y = 4;

    private int selectedModule;
    private int selectedCmd;
    private byte[] draftData = new byte[0];

    private boolean modulePanelOpen = false;
    private boolean commandPanelOpen = false;
    private boolean initialized = false;

    private final List<TextFieldWidget> fieldInputs = new ArrayList<>();
    private TextFieldWidget rawInput;
    private ProtocolRegistry.Command activeCommand;
    private String statusMessage = "";

    protected AbstractProtocolEditorScreen(
            T handler,
            PlayerInventory inventory,
            Text title
    ) {
        super(handler, inventory, title);
        this.backgroundWidth = PANEL_W;
        this.backgroundHeight = PANEL_H;
        this.titleX = 8;
        this.titleY = 6;
    }

    /** 子类构建底部按钮（保存、经典页面、发送等） */
    protected abstract void buildFooterButtons(int x, int y);

    /** 保存当前编辑内容（子类实现具体的数据包） */
    protected abstract void onSave(byte module, byte command, byte[] data);

    /** 发送当前编辑内容（子类按需实现，默认不发送） */
    protected void onSend(byte module, byte command, byte[] data) {
    }

    @Override
    protected void init() {
        super.init();
        if (!initialized) {
            selectedModule = handler.getModule() & 0xFF;
            selectedCmd = handler.getCommand() & 0xFF;
            byte[] data = handler.getData();
            draftData = data != null ? data.clone() : new byte[0];
            initialized = true;
        }
        rebuild();
    }

    // ================= UI 构建 =================

    protected void rebuild() {
        this.setFocused(null);
        this.clearChildren();
        fieldInputs.clear();
        rawInput = null;

        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;

        // 模块 / 命令 选择按钮
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("模块: " + moduleLabel(selectedModule)),
                        b -> {
                            captureDraftFromUi();
                            modulePanelOpen = !modulePanelOpen;
                            commandPanelOpen = false;
                            rebuild();
                        })
                .dimensions(x + 8, y + 18, 150, 20)
                .build());

        ProtocolRegistry.Module module = ProtocolRegistry.findModule(selectedModule);
        boolean hasCommands = module != null && module.hasCommands();
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("命令: " + commandLabel(selectedModule, selectedCmd)),
                        b -> {
                            if (!hasCommands) {
                                statusMessage = "该模块暂无命令定义（原始数据模式）";
                                rebuild();
                                return;
                            }
                            captureDraftFromUi();
                            commandPanelOpen = !commandPanelOpen;
                            modulePanelOpen = false;
                            rebuild();
                        })
                .dimensions(x + 162, y + 18, 150, 20)
                .build());

        if (modulePanelOpen) {
            buildModuleGrid(x, y);
            return;
        }
        if (commandPanelOpen) {
            buildCommandGrid(x, y, module);
            return;
        }

        activeCommand = ProtocolRegistry.findCommand(selectedModule, selectedCmd);
        boolean lengthMatches = activeCommand != null
                && draftData.length == activeCommand.dataLength();
        if (activeCommand != null && lengthMatches) {
            buildFieldInputs(x, y);
        } else {
            buildRawInput(x, y);
        }

        buildFooterButtons(x, y);
    }

    private void buildModuleGrid(int x, int y) {
        List<ProtocolRegistry.Module> modules = ProtocolRegistry.modules();
        for (int i = 0; i < modules.size(); i++) {
            ProtocolRegistry.Module module = modules.get(i);
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int bx = x + 8 + col * (GRID_BTN_W + GRID_GAP_X);
            int by = y + 44 + row * (GRID_BTN_H + GRID_GAP_Y);
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(String.format("%02X %s", module.id(), module.name())),
                            b -> selectModule(module.id()))
                    .dimensions(bx, by, GRID_BTN_W, GRID_BTN_H)
                    .build());
        }
    }

    private void buildCommandGrid(int x, int y, ProtocolRegistry.Module module) {
        if (module == null) {
            return;
        }
        List<ProtocolRegistry.Command> commands = module.commands();
        int index = 0;
        for (ProtocolRegistry.Command command : commands) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int bx = x + 8 + col * (GRID_BTN_W + GRID_GAP_X);
            int by = y + 44 + row * (GRID_BTN_H + GRID_GAP_Y);
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(String.format("%02X %s", command.id(), command.name())),
                            b -> selectCommand(command.id()))
                    .dimensions(bx, by, GRID_BTN_W, GRID_BTN_H)
                    .build());
            index++;
        }
        // GPIO 预留：输入设置暂未提供协议
        if (module.id() == 0x10) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int bx = x + 8 + col * (GRID_BTN_W + GRID_GAP_X);
            int by = y + 44 + row * (GRID_BTN_H + GRID_GAP_Y);
            addDrawableChild(ButtonWidget.builder(
                            Text.literal("输入设置(预留)"),
                            b -> {
                                statusMessage = "输入设置协议暂未提供（预留位）";
                                commandPanelOpen = false;
                                rebuild();
                            })
                    .dimensions(bx, by, GRID_BTN_W, GRID_BTN_H)
                    .build());
        }
    }

    private void buildFieldInputs(int x, int y) {
        int offset = 0;
        List<ProtocolRegistry.Field> fields = activeCommand.fields();
        for (int i = 0; i < fields.size(); i++) {
            ProtocolRegistry.Field field = fields.get(i);
            int col = i % 2;
            int row = i / 2;
            int fx = x + 8 + col * 156;
            int fy = y + 126 + row * 30;
            TextFieldWidget input = new TextFieldWidget(
                    textRenderer, fx, fy, 148, 18, Text.literal(field.label()));
            input.setText(formatFieldValue(field, offset));
            input.setMaxLength(6);
            addDrawableChild(input);
            fieldInputs.add(input);
            offset += field.bytes();
        }
    }

    private void buildRawInput(int x, int y) {
        rawInput = new TextFieldWidget(
                textRenderer, x + 8, y + 126, 304, 18,
                Text.literal("原始数据（十进制字节）"));
        rawInput.setMaxLength(255 * 4);
        rawInput.setText(formatRawBytes(draftData));
        addDrawableChild(rawInput);
    }

    // ================= 选择逻辑 =================

    private void selectModule(int id) {
        captureDraftFromUi();
        int oldModule = selectedModule;
        int oldCmd = selectedCmd;
        selectedModule = id;
        modulePanelOpen = false;
        commandPanelOpen = false;

        ProtocolRegistry.Module module = ProtocolRegistry.findModule(id);
        if (module != null && module.hasCommands()) {
            boolean keepCmd = oldModule == id
                    && ProtocolRegistry.findCommand(id, oldCmd) != null;
            selectedCmd = keepCmd ? oldCmd : module.commands().get(0).id();
            ProtocolRegistry.Command command = ProtocolRegistry.findCommand(selectedModule, selectedCmd);
            boolean sameProtocol = oldModule == id && oldCmd == selectedCmd;
            if (command != null && (!sameProtocol || draftData.length != command.dataLength())) {
                draftData = new byte[command.dataLength()];
            }
        }
        // 没有命令定义的模块：保留当前 cmd 与原始数据，避免丢失
        rebuild();
    }

    private void selectCommand(int id) {
        captureDraftFromUi();
        int oldModule = selectedModule;
        int oldCmd = selectedCmd;
        selectedCmd = id;
        modulePanelOpen = false;
        commandPanelOpen = false;

        ProtocolRegistry.Command command = ProtocolRegistry.findCommand(selectedModule, id);
        if (command != null
                && (oldModule != selectedModule || oldCmd != id || draftData.length != command.dataLength())) {
            draftData = new byte[command.dataLength()];
        }
        rebuild();
    }

    // ================= 数据编解码（十进制 UI <-> 原始字节） =================

    private String formatFieldValue(ProtocolRegistry.Field field, int offset) {
        long value = 0;
        for (int i = 0; i < field.bytes(); i++) {
            value = (value << 8) | (draftData[offset + i] & 0xFF);
        }
        return Long.toString(value);
    }

    private String formatRawBytes(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(b & 0xFF);
        }
        return sb.toString();
    }

    /** 从当前控件读取数据；无效时返回 null（不覆盖 draft） */
    private byte[] readDataFromUi() {
        if (rawInput != null) {
            return parseDecimalBytes(rawInput.getText());
        }
        if (activeCommand == null || fieldInputs.size() != activeCommand.fields().size()) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(activeCommand.dataLength());
        List<ProtocolRegistry.Field> fields = activeCommand.fields();
        for (int i = 0; i < fields.size(); i++) {
            ProtocolRegistry.Field field = fields.get(i);
            long value;
            try {
                value = Long.parseLong(fieldInputs.get(i).getText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
            long max = field.bytes() == 1 ? 0xFFL : 0xFFFFL;
            if (value < 0 || value > max) {
                return null;
            }
            if (field.bytes() == 1) {
                out.write((int) value);
            } else {
                out.write((int) ((value >> 8) & 0xFF));
                out.write((int) (value & 0xFF));
            }
        }
        return out.toByteArray();
    }

    private byte[] parseDecimalBytes(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new byte[0];
        }
        String[] parts = trimmed.split("\\s+");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int value;
            try {
                value = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            result[i] = (byte) value;
        }
        return result;
    }

    private void captureDraftFromUi() {
        byte[] data = readDataFromUi();
        if (data != null) {
            draftData = data;
        }
    }

    /** 把当前 UI 数据写回 handler（不发包），供页面切换/子类使用 */
    protected boolean commitToHandler() {
        byte[] data = readDataFromUi();
        if (data == null) {
            statusMessage = "输入无效：请输入十进制数值并检查范围";
            return false;
        }
        if (data.length > 255) {
            statusMessage = "数据长度超过 255 字节";
            return false;
        }
        draftData = data;
        handler.setModule((byte) selectedModule);
        handler.setCommand((byte) selectedCmd);
        handler.setLength((byte) (data.length & 0xFF));
        handler.setData(data);
        return true;
    }

    // ================= 保存 / 发送 =================

    protected void save() {
        if (!commitToHandler()) {
            return;
        }
        onSave((byte) selectedModule, (byte) selectedCmd, draftData);
        statusMessage = String.format("已保存: module=0x%02X cmd=0x%02X len=%d",
                selectedModule, selectedCmd, draftData.length);
        rebuild();
    }

    protected void sendNow() {
        if (!commitToHandler()) {
            return;
        }
        onSend((byte) selectedModule, (byte) selectedCmd, draftData);
        statusMessage = String.format("已提交发送: module=0x%02X cmd=0x%02X len=%d",
                selectedModule, selectedCmd, draftData.length);
        rebuild();
    }

    // ================= 显示 =================

    private String moduleLabel(int id) {
        ProtocolRegistry.Module module = ProtocolRegistry.findModule(id);
        String name = module != null ? module.name() : "未知";
        return String.format("%s (0x%02X)", name, id);
    }

    private String commandLabel(int moduleId, int cmdId) {
        ProtocolRegistry.Command command = ProtocolRegistry.findCommand(moduleId, cmdId);
        String name = command != null ? command.name() : "未定义";
        return String.format("%s (0x%02X)", name, cmdId);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF555555);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "协议编辑器", 8, 6, 0xFFFFFF, false);

        if (modulePanelOpen) {
            context.drawText(textRenderer, "请选择模块：", 8, 112, 0xFFD7A0, false);
            return;
        }
        if (commandPanelOpen) {
            context.drawText(textRenderer, "请选择命令：", 8, 112, 0xFFD7A0, false);
            return;
        }

        if (activeCommand != null && !fieldInputs.isEmpty()) {
            context.drawText(textRenderer,
                    String.format("协议: %s / %s    data=%d 字节",
                            moduleLabel(selectedModule), commandLabel(selectedModule, selectedCmd),
                            activeCommand.dataLength()),
                    8, 96, 0xFFD7A0, false);

            List<ProtocolRegistry.Field> fields = activeCommand.fields();
            for (int i = 0; i < fields.size(); i++) {
                int col = i % 2;
                int row = i / 2;
                int lx = 8 + col * 156;
                int ly = 114 + row * 30;
                context.drawText(textRenderer,
                        fields.get(i).label() + " (" + fields.get(i).bytes() + "B):",
                        lx, ly, 0xFFFFFF, false);
            }
        } else {
            context.drawText(textRenderer, "原始数据（十进制字节，空格分隔）：", 8, 114, 0xFFD7A0, false);
            if (activeCommand != null) {
                context.drawText(textRenderer,
                        String.format("数据长度与协议不符（期望 %d，实际 %d），已保留原始数据",
                                activeCommand.dataLength(), draftData.length),
                        8, 152, 0xFFAA55, false);
            } else {
                context.drawText(textRenderer,
                        "未知模块/命令，已保留原始数据不改动",
                        8, 152, 0xFFAA55, false);
            }
        }

        if (!statusMessage.isEmpty()) {
            context.drawText(textRenderer, statusMessage, 8, 216, 0xFFFF80, false);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框聚焦时按 E 不关闭界面，让 e 正常输入
        if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)
                && getFocused() instanceof TextFieldWidget) {
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}