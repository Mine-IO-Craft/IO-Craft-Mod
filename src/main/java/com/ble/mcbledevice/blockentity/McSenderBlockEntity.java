package com.ble.mcbledevice.blockentity;

import com.ble.mcbledevice.blocks.ModBlockEntities;
import com.ble.mcbledevice.handler.BleHandler;
import com.ble.mcbledevice.handler.SerialHandler;
import com.ble.mcbledevice.tools.NetworkHandler;
import com.ble.mcbledevice.tools.PacketBuilderTool;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;


public class McSenderBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(McSenderBlockEntity.class);


    public byte module = 0;
    public byte command = 0;
    public byte length = 0;

    public byte[] data = new byte[0];


    //保存红石状态
    public boolean lastPowered = false;



    public McSenderBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                ModBlockEntities.MC_SENDER,
                pos,
                state
        );
    }



    //写入世界存档
    @Override
    protected void writeNbt(
            NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup
    ) {

        super.writeNbt(nbt,registryLookup);
        nbt.putByte(
                "Module",
                module
        );
        nbt.putByte(
                "Command",
                command
        );
        nbt.putByte(
                "Length",
                length
        );
        nbt.putByteArray(
                "Data",
                data
        );
    }

    //读取世界存档
    @Override
    protected void readNbt(
            NbtCompound nbt,RegistryWrapper.WrapperLookup registryLookup
    ) {
        super.readNbt(nbt,registryLookup);
        module = nbt.getByte(
                "Module"
        );
        command = nbt.getByte(
                "Command"
        );
        length = nbt.getByte(
                "Length"
        );
        data = nbt.getByteArray(
                "Data"
        );
    }

    public void onRedstoneUpdate(boolean powered)
    {
        if (powered && !lastPowered)
        {
            sendPacket();
        }

        lastPowered = powered;
    }

    private void sendPacket()
    {
        // 协议长度字段只有 1 字节：按无符号解析（& 0xFF），并防止越界
        int len = length & 0xFF;
        if (len > data.length)
        {
            len = data.length;
        }
        byte[] packet =
                PacketBuilderTool.buildPacket(
                        module,
                        command,
                        Arrays.copyOf(
                                data,
                                len
                        )
                );
        LOGGER.debug("Packet length = {}", packet.length);
        SerialHandler.sendData(packet);
        NetworkHandler.sendData(packet);
        BleHandler.sendData(packet);
    }

}

