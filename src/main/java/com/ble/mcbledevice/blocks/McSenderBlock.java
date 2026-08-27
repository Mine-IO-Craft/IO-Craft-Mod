package com.ble.mcbledevice.blocks;

import com.ble.mcbledevice.blockentity.McSenderBlockEntity;
import com.ble.mcbledevice.menu.McSenderScreenHandler;
import com.ble.mcbledevice.network.McSenderOpenData;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class McSenderBlock extends BlockWithEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(McSenderBlock.class);

    // 1.21 开始必须提供 CODEC
    public static final MapCodec<McSenderBlock> CODEC =
            createCodec(McSenderBlock::new);

    public McSenderBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new McSenderBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit
    ) {

        if (!world.isClient()) {

            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof McSenderBlockEntity sender) {
                //在右键打开 GUI 的时候，打印 BlockEntity 当前值
                LOGGER.debug("打开发送器 GUI，module={}", sender.module);

                player.openHandledScreen(new ExtendedScreenHandlerFactory<>() {

                    @Override
                    public McSenderOpenData getScreenOpeningData(ServerPlayerEntity player) {
                        return new McSenderOpenData(
                                pos,
                                sender.module,
                                sender.command,
                                sender.length,
                                sender.data
                        );
                    }

                    @Override
                    public Text getDisplayName() {
                        return Text.literal("MC Sender");
                    }

                    @Override
                    public ScreenHandler createMenu(
                            int syncId,
                            PlayerInventory inventory,
                            PlayerEntity player
                    ) {
                        return new McSenderScreenHandler(
                                syncId,
                                inventory,
                                sender
                        );
                    }
                });
            }

        }

        return ActionResult.SUCCESS;

    }

    @Override
    protected void neighborUpdate(
            BlockState state,
            World world,
            BlockPos pos,
            Block sourceBlock,
            BlockPos sourcePos,
            boolean notify
    )
    {
        super.neighborUpdate(
                state,
                world,
                pos,
                sourceBlock,
                sourcePos,
                notify
        );

        if (world.isClient())
        {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity instanceof McSenderBlockEntity sender)
        {
            boolean powered =
                    world.isReceivingRedstonePower(pos);

            sender.onRedstoneUpdate(powered);
        }
    }
}
