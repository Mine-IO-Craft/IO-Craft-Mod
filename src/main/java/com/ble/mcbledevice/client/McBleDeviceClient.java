package com.ble.mcbledevice.client;

import com.ble.mcbledevice.client.screen.McBleDeviceConfigScreen;
import com.ble.mcbledevice.menu.ModScreenHandlers;
import com.ble.mcbledevice.client.screen.McSenderScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;


public class McBleDeviceClient implements ClientModInitializer {

    private static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {

        HandledScreens.register(
                ModScreenHandlers.MC_SENDER_SCREEN_HANDLER,
                McSenderScreen::new
        );

        // 按 P 打开自定义设置页
        openConfigKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.mcbledevice.config",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_P,
                        "category.mcbledevice"
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigKey.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new McBleDeviceConfigScreen());
            }
        });
    }
}
