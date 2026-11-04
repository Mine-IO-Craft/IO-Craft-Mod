package com.ble.mcbledevice.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * 打开 ESP32-S3 物品界面时随 OpenScreen 包同步的数据。
 * 与方块发送器的 McSenderOpenData 类似，但没有方块坐标。
 */
public record Esp32S3OpenData(
        byte module,
        byte command,
        byte length,
        byte[] data
) {

    public static final PacketCodec<PacketByteBuf, Esp32S3OpenData> CODEC =
            PacketCodec.of(
                    (openData, buf) -> {
                        buf.writeByte(openData.module);
                        buf.writeByte(openData.command);
                        buf.writeByte(openData.length);
                        buf.writeByteArray(openData.data);
                    },
                    buf -> new Esp32S3OpenData(
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByteArray()
                    )
            );
}