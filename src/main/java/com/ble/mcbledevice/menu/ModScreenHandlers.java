package com.ble.mcbledevice.menu;


import com.ble.mcbledevice.McBleDevice;
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

    public static void register() {

        Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(
                        McBleDevice.MOD_ID,
                        "mc_sender"
                ),
                MC_SENDER_SCREEN_HANDLER
        );


        McBleDevice.LOGGER.info(
                "Registering Screen Handlers"
        );
    }
}
