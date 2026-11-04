package com.ble.mcbledevice.client.screen;

import com.ble.mcbledevice.McBleDevice;
import com.ble.mcbledevice.menu.McSenderScreenHandler;
import com.ble.mcbledevice.network.McSenderUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;



public class McSenderScreen extends HandledScreen<McSenderScreenHandler> {

    private TextFieldWidget moduleField;
    private TextFieldWidget commandField;
    private TextFieldWidget lengthField;
    private TextFieldWidget dataField;

    //给data数组用的，就他是数组类型，mod cmd len都是变量
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes) {
            sb.append(
                    String.format("%02X ", b)
            );
        }
        return sb.toString().trim();
    }

    // Length 为只读展示，始终跟随 Data 的实际字节数
    private void updateLengthDisplay() {
        try {
            int len = parseHex(dataField.getText()).length & 0xFF;
            lengthField.setText(String.format("%02X", len));
        } catch (Exception e) {
            lengthField.setText("--");
        }
    }

    //给data数组用的
    private byte[] parseHex(String text) {
        text = text.trim();
        if(text.isEmpty()) {
            return new byte[0];
        }
        String[] split = text.split("\\s+");
        byte[] result = new byte[split.length];
        for(int i = 0; i < split.length; i++) {
            result[i] = (byte)Integer.parseInt(split[i], 16);
        }
        return result;
    }


    public McSenderScreen(
            McSenderScreenHandler handler,
            PlayerInventory inventory,
            Text title
    ) {
        super(handler, inventory, title);

        this.backgroundWidth = 220;
        this.backgroundHeight = 180;

        this.titleX = 10;
        this.titleY = 10;
    }

    //保存发送器数据到服务端
    private void save() {
        McBleDevice.LOGGER.debug("保存按钮被点击");
        try {

            byte module = (byte)Integer.parseInt(moduleField.getText().trim(),16);
            byte command = (byte)Integer.parseInt(commandField.getText().trim(),16);
            byte[] data = parseHex(dataField.getText());
            if (data.length > 255) {
                McBleDevice.LOGGER.warn(
                        "数据长度 {} 字节超过协议上限 255，已取消发送",
                        data.length
                );
                return;
            }
            byte length = (byte)(data.length & 0xFF);

            McBleDevice.LOGGER.debug("发送数据包: module={} command={} length={}",
                    module, command, length);
            ClientPlayNetworking.send(
                    new McSenderUpdatePayload(
                            handler.getPos(),
                            module,
                            command,
                            length,
                            data
                    )
            );
        }
        catch (Exception e) {
            McBleDevice.LOGGER.error("保存发送器数据失败", e);

        }

    }
    @Override
    protected void drawBackground(
            DrawContext context,
            float delta,
            int mouseX,
            int mouseY
    ) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.fill(
                x,
                y,
                x + backgroundWidth,
                y + backgroundHeight,
                0xFF555555
        );
    }


    @Override
    protected void drawForeground(
            DrawContext context,
            int mouseX,
            int mouseY
    ) {

        context.drawText(
                textRenderer,
                "MC Sender",
                10,
                10,
                0xFFFFFF,
                false
        );

        context.drawText(
                textRenderer,
                "Module:",
                10,
                40,
                0xFFFFFF,
                false
        );

        context.drawText(
                textRenderer,
                "Command:",
                10,
                70,
                0xFFFFFF,
                false
        );

        context.drawText(
                textRenderer,
                "Length:",
                10,
                100,
                0xFFFFFF,
                false
        );

        context.drawText(
                textRenderer,
                "Data:",
                10,
                130,
                0xFFFFFF,
                false
        );
    }


    @Override
    public void render(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {

        renderBackground(
                context,
                mouseX,
                mouseY,
                delta
        );

        super.render(
                context,
                mouseX,
                mouseY,
                delta
        );

        drawMouseoverTooltip(
                context,
                mouseX,
                mouseY
        );
    }


    @Override
    protected void init() {
        super.init();

        McBleDevice.LOGGER.debug("初始化发送器界面: module={} command={} length={}",
                handler.getModule(), handler.getCommand(), handler.getLength());


        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;


        moduleField = new TextFieldWidget(
                textRenderer,
                x + 80,
                y + 35,
                100,
                20,
                Text.literal("Module")
        );


        commandField = new TextFieldWidget(
                textRenderer,
                x + 80,
                y + 65,
                100,
                20,
                Text.literal("Command")
        );


        lengthField = new TextFieldWidget(
                textRenderer,
                x + 80,
                y + 95,
                100,
                20,
                Text.literal("Length")
        );
        // Length 只读展示，由 Data 实时计算
        lengthField.setEditable(false);


        dataField = new TextFieldWidget(
                textRenderer,
                x + 80,
                y + 125,
                100,
                20,
                Text.literal("Data")
        );

        // 打开屏幕时同步数据已随 OpenScreen 包一起到达，直接填充，无需等待异步包
        moduleField.setText(String.format("%02X", handler.getModule() & 0xFF));
        commandField.setText(String.format("%02X", handler.getCommand() & 0xFF));
        lengthField.setText(String.format("%02X", handler.getLength() & 0xFF));
        dataField.setText(bytesToHex(handler.getData()));
        dataField.setChangedListener(s -> updateLengthDisplay());
        updateLengthDisplay();

        addDrawableChild(moduleField);
        addDrawableChild(commandField);
        addDrawableChild(lengthField);
        addDrawableChild(dataField);
        addDrawableChild(
                ButtonWidget.builder(
                                Text.literal("Save"),
                                button -> {
                                    save();
                                }
                        )
                        .dimensions(
                                this.width / 2 - 84,
                                this.height / 2 + 70,
                                80,
                                20
                        )
                        .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                                Text.literal("协议编辑器"),
                                button -> openProtocolEditor()
                        )
                        .dimensions(
                                this.width / 2 + 4,
                                this.height / 2 + 70,
                                80,
                                20
                        )
                        .build()
        );

    }

    /**
     * 切换前把旧页面输入同步到 handler（不发包），避免草稿丢失
     */
    private void openProtocolEditor() {
        try {
            byte module = (byte) Integer.parseInt(moduleField.getText().trim(), 16);
            byte command = (byte) Integer.parseInt(commandField.getText().trim(), 16);
            byte[] data = parseHex(dataField.getText());
            handler.setModule(module);
            handler.setCommand(command);
            handler.setLength((byte) (data.length & 0xFF));
            handler.setData(data);
        } catch (Exception e) {
            McBleDevice.LOGGER.warn("切换协议编辑器前解析旧页面输入失败", e);
        }
        this.client.setScreen(new McSenderProtocolScreen(
                this.handler,
                this.client.player.getInventory(),
                Text.literal("协议编辑器")
        ));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框聚焦时按 E：不关闭界面，让 'e' 正常输入
        // （原版 HandledScreen 会把 E 当作“打开/关闭物品栏”键直接关屏）
        if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)
                && getFocused() instanceof TextFieldWidget) {
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


}
