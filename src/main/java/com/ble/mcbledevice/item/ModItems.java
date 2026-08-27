package com.ble.mcbledevice.item;

import com.ble.mcbledevice.blocks.ModBlocks;
import com.ble.mcbledevice.McBleDevice;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;





//注册物品
public class ModItems {

    //注册MC发送器 方块
    public static final Item mcSender = registerItems("mc_sender_block", new BlockItem(ModBlocks.MC_SENDER_BLOCK,new Item.Settings()));

    //注册物品方法
    private static Item registerItems(String id,Item item){
        return Registry.register(Registries.ITEM, Identifier.of(McBleDevice.MOD_ID,id),item);
    }

    public static void registerModItems(){
        McBleDevice.LOGGER.info("Registering Items");
    }
}
