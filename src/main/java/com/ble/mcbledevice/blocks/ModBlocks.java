package com.ble.mcbledevice.blocks;

import com.ble.mcbledevice.McBleDevice;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;


public class ModBlocks {
    //注册发送器方块
    public static final Block MC_SENDER_BLOCK =
            registerBlock(
                    "mc_sender_block",
                    new McSenderBlock(AbstractBlock.Settings.create().strength(2.0F, 3.0F).sounds(BlockSoundGroup.WOOD))
            );


    private static Block registerBlock(String id, Block block){
        return Registry.register(
                Registries.BLOCK,
                Identifier.of(McBleDevice.MOD_ID, id),
                block
        );
    }
    public static void registerModBlocks(){
        McBleDevice.LOGGER.info("Registering Blocks");
    }
}
