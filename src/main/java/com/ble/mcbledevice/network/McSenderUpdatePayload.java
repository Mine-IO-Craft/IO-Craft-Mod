package com.ble.mcbledevice.network;


import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;


public record McSenderUpdatePayload(
        BlockPos pos,
        byte module,
        byte command,
        byte length,
        byte[] data
) implements CustomPayload {



    public static final Id<McSenderUpdatePayload> ID =
            new Id<>(
                    Identifier.of(
                            "mcbledevice",
                            "mc_sender_update"
                    )
            );


    public static final PacketCodec<PacketByteBuf, McSenderUpdatePayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> {

                        buf.writeBlockPos(payload.pos);
                        buf.writeByte(payload.module);
                        buf.writeByte(payload.command);
                        buf.writeByte(payload.length);
                        buf.writeByteArray(payload.data);

                    },

                    buf -> new McSenderUpdatePayload(

                            buf.readBlockPos(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByteArray()

                    )
            );



    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}