package com.ble.mcbledevice.blockentity;

import com.ble.mcbledevice.blocks.ModBlockEntities;
import com.ble.mcbledevice.handler.BleHandler;
import com.ble.mcbledevice.handler.SerialHandler;
import com.ble.mcbledevice.tools.NetworkHandler;
import com.ble.mcbledevice.tools.PacketBuilderTool;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * MC发送器方块实体：保存 cmd / mod / data / length。
 * 这些字段在服务端被修改（C2S 数据包），通过 NBT 持久化，
 * 并通过 {@link #markUpdated()} 同步到客户端用于方块表面文字渲染。
 */
public class McSenderBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(McSenderBlockEntity.class);

    public byte module = 0;
    public byte command = 0;
    public byte length = 0;

    public byte[] data = new byte[0];

    // 保存红石状态
    public boolean lastPowered = false;

    // ============ 客户端显示缓存（不写入 NBT，仅供方块实体渲染器使用） ============
    // 数据变化（readNbt 被调用）时置 true；渲染器重建一次后置 false，避免每帧拼字符串。
    public boolean displayDirty = true;
    public String displayCmdRow = "";
    public String displayDataLabel = "DATA:";
    public String[] displayDataRows = new String[0];
    public int displayCmdWidth = 0;
    public int displayDataLabelWidth = 0;
    public int displayDataMaxRowWidth = 0;
    public int displayBlockWidth = 0;
    public float displayScale = 0.01f;

    public McSenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MC_SENDER, pos, state);
    }

    // 写入世界存档
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putByte("Module", module);
        nbt.putByte("Command", command);
        nbt.putByte("Length", length);
        nbt.putByteArray("Data", data);
    }

    // 读取世界存档 / 接收同步包
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        module = nbt.getByte("Module");
        command = nbt.getByte("Command");
        length = nbt.getByte("Length");
        data = nbt.getByteArray("Data");
        // 数据已变化，下次渲染时重建表面文字布局
        displayDirty = true;
    }

    /**
     * 随区块一起发给客户端的 NBT：包含发送器全部数据，客户端据此渲染方块表面文字。
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    /**
     * 服务端数据变化后调用：
     * 1. markDirty() 让区块落盘；
     * 2. updateListeners 触发区块/渲染刷新；
     * 3. 显式发送 BlockEntityUpdateS2CPacket，确保所有客户端立即用新数据刷新表面文字。
     */
    public void markUpdated() {
        markDirty();
        if (world == null || world.isClient) {
            return;
        }
        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        Packet<ClientPlayPacketListener> packet = BlockEntityUpdateS2CPacket.create(this);
        for (ServerPlayerEntity player : PlayerLookup.tracking((ServerWorld) world, pos)) {
            player.networkHandler.sendPacket(packet);
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (powered && !lastPowered) {
            sendPacket();
        }
        lastPowered = powered;
    }

    private void sendPacket() {
        // 协议长度字段只有 1 字节：按无符号解析（& 0xFF），并防止越界
        int len = length & 0xFF;
        if (len > data.length) {
            len = data.length;
        }
        byte[] packet = PacketBuilderTool.buildPacket(
                module,
                command,
                Arrays.copyOf(data, len)
        );
        LOGGER.debug("Packet length = {}", packet.length);
        SerialHandler.sendData(packet);
        NetworkHandler.sendData(packet);
        BleHandler.sendData(packet);
    }
}