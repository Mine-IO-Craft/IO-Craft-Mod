package com.ble.mcbledevice.item;

import com.ble.mcbledevice.menu.Esp32S3ScreenHandler;
import com.ble.mcbledevice.network.Esp32S3OpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * ESP32-S3 手持物品：右键（空气或方块）打开协议编辑器。
 * 数据保存在物品栈的 custom_data 中，界面逻辑完全复用发送器的协议编辑器。
 */
public class Esp32S3Item extends Item {

    public Esp32S3Item(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity serverPlayer) {
            openEditor(serverPlayer, stack);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity user = context.getPlayer();
        if (user instanceof ServerPlayerEntity serverPlayer) {
            openEditor(serverPlayer, user.getStackInHand(context.getHand()));
        }
        return ActionResult.SUCCESS;
    }

    private void openEditor(ServerPlayerEntity player, ItemStack stack) {
        Esp32S3Data data = Esp32S3Data.read(stack);
        player.openHandledScreen(new ExtendedScreenHandlerFactory<Esp32S3OpenData>() {
            @Override
            public Esp32S3OpenData getScreenOpeningData(ServerPlayerEntity serverPlayer) {
                return new Esp32S3OpenData(
                        data.module(),
                        data.command(),
                        data.length(),
                        data.data()
                );
            }

            @Override
            public Text getDisplayName() {
                return Text.literal("ESP32-S3");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                return new Esp32S3ScreenHandler(syncId, inventory, stack);
            }
        });
    }
}