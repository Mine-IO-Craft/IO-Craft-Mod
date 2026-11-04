package com.ble.mcbledevice.menu;


import com.ble.mcbledevice.McBleDevice;
import com.ble.mcbledevice.network.Esp32S3OpenData;
import com.ble.mcbledevice.network.McSenderOpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public class ModScreenHandlers {


    /**
     * 扩展屏幕处理器：打开屏幕时把位置和发送器内容作为同步数据一起发给客户端。
     */
    public static final ExtendedScreenHandlerType<McSenderScreenHandler, McSenderOpenData> MC_SENDER_SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(
                    McSenderScreenHandler::new,
                    McSenderOpenData.CODEC
            );

    /**
     * ESP32-S3 手持物品的界面处理器：开屏时同步物品内保存的原始协议数据。
     */
    public static final ExtendedScreenHandlerType<Esp32S3ScreenHandler, Esp32S3OpenData> ESP32_S3_SCREEN_HANDLER =
            new ExtendedScreenHandlerType<>(
                    Esp32S3ScreenHandler::new,
                    Esp32S3OpenData.CODEC
            );

    public static void register() {

        Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(
                        McBleDevice.MOD_ID,
                        "mc_sender"
                ),
                MC_SENDER_SCREEN_HANDLER
        );


        Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(
                        McBleDevice.MOD_ID,
                        "esp32_s3"
                ),
                ESP32_S3_SCREEN_HANDLER
        );

        McBleDevice.LOGGER.info(
                "Registering Screen Handlers"
        );
    }
}
