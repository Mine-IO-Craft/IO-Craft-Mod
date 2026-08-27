package com.ble.mcbledevice.menu;


import com.ble.mcbledevice.blockentity.McSenderBlockEntity;
import com.ble.mcbledevice.network.McSenderOpenData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;


public class McSenderScreenHandler extends ScreenHandler {


    private final McSenderBlockEntity blockEntity;
    private BlockPos pos;
    private byte module;
    private byte command;
    private byte length;
    private byte[] data = new byte[0];
    private boolean received = false;

    public boolean isReceived()
    {
        return received;
    }

    public void setReceived(boolean received)
    {
        this.received = received;
    }


    /**
     * 客户端构造：数据随 OpenScreen 包同步到达，无竞态。
     */
    public McSenderScreenHandler(
            int syncId,
            PlayerInventory inventory,
            McSenderOpenData openData
    ) {
        super(
                ModScreenHandlers.MC_SENDER_SCREEN_HANDLER,
                syncId
        );

        this.blockEntity = null;
        this.pos = openData.pos();
        this.module = openData.module();
        this.command = openData.command();
        this.length = openData.length();
        this.data = openData.data();
        this.received = true;
    }


    /**
     * 服务端构造
     */
    public McSenderScreenHandler(
            int syncId,
            PlayerInventory inventory,
            McSenderBlockEntity blockEntity
    ) {

        super(
                ModScreenHandlers.MC_SENDER_SCREEN_HANDLER,
                syncId
        );


        this.blockEntity = blockEntity;
        this.pos = blockEntity.getPos();

    }



    public BlockPos getPos()
    {
        return pos;
    }
    public void setPos(BlockPos pos)
    {
        this.pos = pos;
    }

    public McSenderBlockEntity getBlockEntity()
    {
        return blockEntity;
    }
    public byte getModule()
    {
        return module;
    }
    public byte getCommand()
    {
        return command;
    }
    public byte getLength()
    {
        return length;
    }

    public byte[] getData()
    {
        return data;
    }
    public void setModule(byte module)
    {
        this.module = module;
    }
    public void setCommand(byte command)
    {
        this.command = command;
    }
    public void setLength(byte length)
    {
        this.length = length;
    }
    public void setData(byte[] data)
    {
        this.data = data;
    }

    @Override
    public boolean canUse(PlayerEntity player)
    {
        // 客户端构造的 handler（数据来自开屏包）不做校验；服务端校验玩家与方块距离
        if (blockEntity == null) {
            return true;
        }
        return player.getBlockPos().getSquaredDistance(pos) <= 4096.0;
    }



    @Override
    public ItemStack quickMove(
            PlayerEntity player,
            int slot
    )
    {
        return ItemStack.EMPTY;
    }
}
