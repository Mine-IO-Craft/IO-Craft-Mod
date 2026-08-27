package com.ble.mcbledevice.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * 打开发送器 GUI 时的同步数据：随 OpenScreen 包一起到达客户端，
 * 避免单独发包导致的时序竞态（坐标丢失、字段为空）。
 */
public record McSenderOpenData(
        BlockPos pos,
        byte module,
        byte command,
        byte length,
        byte[] data
) {

    public static final PacketCodec<PacketByteBuf, McSenderOpenData> CODEC =
            PacketCodec.of(
                    (openData, buf) -> {
                        buf.writeBlockPos(openData.pos);
                        buf.writeByte(openData.module);
                        buf.writeByte(openData.command);
                        buf.writeByte(openData.length);
                        buf.writeByteArray(openData.data);
                    },
                    buf -> new McSenderOpenData(
                            buf.readBlockPos(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByteArray()
                    )
            );
}
