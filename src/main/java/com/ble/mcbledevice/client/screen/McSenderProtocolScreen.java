package com.ble.mcbledevice.client.screen;

import com.ble.mcbledevice.menu.McSenderScreenHandler;
import com.ble.mcbledevice.network.McSenderUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * 方块发送器的协议编辑页面（保存 + 切换到经典页面）。
 * 所有协议编辑逻辑都在 AbstractProtocolEditorScreen 中，物品版本共用同一套。
 */
public class McSenderProtocolScreen extends AbstractProtocolEditorScreen<McSenderScreenHandler> {

    public McSenderProtocolScreen(
            McSenderScreenHandler handler,
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
        addDrawableChild(ButtonWidget.builder(Text.literal("经典页面"), b -> openClassicScreen())
                .dimensions(x + 166, y + 240, 146, 20)
                .build());
    }

    @Override
    protected void onSave(byte module, byte command, byte[] data) {
        ClientPlayNetworking.send(new McSenderUpdatePayload(
                handler.getPos(),
                module,
                command,
                (byte) (data.length & 0xFF),
                data
        ));
    }

    private void openClassicScreen() {
        if (!commitToHandler()) {
            return;
        }
        this.client.setScreen(new McSenderScreen(
                this.handler,
                this.client.player.getInventory(),
                Text.literal("MC Sender")
        ));
    }
}