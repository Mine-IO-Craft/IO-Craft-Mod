package com.ble.mcbledevice.item;

import com.ble.mcbledevice.McBleDevice;
import com.ble.mcbledevice.blocks.ModBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    // 注册 MC 发送器方块物品
    public static final Item mcSender = registerItems("mc_sender_block", new BlockItem(ModBlocks.MC_SENDER_BLOCK, new Item.Settings()));

    // 注册 ESP32-S3 手持物品
    public static final Item ESP32_S3 = registerItems("esp32_s3", new Esp32S3Item(new Item.Settings().maxCount(1)));

    private static Item registerItems(String id, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(McBleDevice.MOD_ID, id), item);
    }

    public static void registerModItems() {
        McBleDevice.LOGGER.info("Registering Items");
    }
}