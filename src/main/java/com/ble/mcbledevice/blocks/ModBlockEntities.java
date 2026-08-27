package com.ble.mcbledevice.blocks;

import com.ble.mcbledevice.McBleDevice;
import com.ble.mcbledevice.blockentity.McSenderBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public class ModBlockEntities {

    public static BlockEntityType<McSenderBlockEntity> MC_SENDER;

    public static void register() {

        MC_SENDER = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(McBleDevice.MOD_ID, "mc_sender"),
                FabricBlockEntityTypeBuilder.create(
                        McSenderBlockEntity::new,
                        ModBlocks.MC_SENDER_BLOCK
                ).build()
        );

    }

}
