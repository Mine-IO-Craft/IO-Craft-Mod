package com.ble.mcbledevice.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

//注册分类
//ItemGroups.REDSTONE即红石元件
public class ModItemGroups {

    public static void registerModItemGroups(){
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE)
                .register(entries ->
                {
                    entries.add(ModItems.mcSender);

                });
    }
}
