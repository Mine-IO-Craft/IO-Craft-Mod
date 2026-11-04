package com.ble.mcbledevice.client.screen;

import com.ble.mcbledevice.menu.Esp32S3ScreenHandler;
import com.ble.mcbledevice.network.Esp32S3UpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * ESP32-S3 手持物品的协议编辑页面。
 * 与方块发送器共用 AbstractProtocolEditorScreen 的全部编辑逻辑，
 * 仅额外提供“发送”按钮：保存只写入物品栈，发送会立即走现有发送链路。
 */
public class Esp32S3ProtocolScreen extends AbstractProtocolEditorScreen<Esp32S3ScreenHandler> {

    public Esp32S3ProtocolScreen(
            Esp32S3ScreenHandler handler,
            PlayerInventory inventory,
            Text title
    ) {
        super(handler, inventory, title);
    }

    @Override
    protected void buildFooterButtons(int x, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> save())
                .dimensions(x + 8, y + 240, 146, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("发送"), b -> sendNow())
                .dimensions(x + 166, y + 240, 146, 20)
                .build());
    }

    @Override
    protected void onSave(byte module, byte command, byte[] data) {
        ClientPlayNetworking.send(new Esp32S3UpdatePayload(
                module,
                command,
                (byte) (data.length & 0xFF),
                data,
                false
        ));
    }

    @Override
    protected void onSend(byte module, byte command, byte[] data) {
        ClientPlayNetworking.send(new Esp32S3UpdatePayload(
                module,
                command,
                (byte) (data.length & 0xFF),
                data,
                true
        ));
    }
}