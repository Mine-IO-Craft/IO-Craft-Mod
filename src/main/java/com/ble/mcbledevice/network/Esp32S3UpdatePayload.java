package com.ble.mcbledevice.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * ESP32-S3 物品界面的 C2S 数据包：
 * sendNow=false 只保存到物品栈；sendNow=true 保存后立即按现有发送链路发送一帧。
 * 帧格式仍然是 module + command + length + data。
 */
public record Esp32S3UpdatePayload(
        byte module,
        byte command,
        byte length,
        byte[] data,
        boolean sendNow
) implements CustomPayload {

    public static final Id<Esp32S3UpdatePayload> ID =
            new Id<>(Identifier.of("mcbledevice", "esp32_s3_update"));

    public static final PacketCodec<PacketByteBuf, Esp32S3UpdatePayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> {
                        buf.writeByte(payload.module);
                        buf.writeByte(payload.command);
                        buf.writeByte(payload.length);
                        buf.writeByteArray(payload.data);
                        buf.writeBoolean(payload.sendNow);
                    },
                    buf -> new Esp32S3UpdatePayload(
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByteArray(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}