package com.ble.mcbledevice.menu;

import com.ble.mcbledevice.item.Esp32S3Data;
import com.ble.mcbledevice.network.Esp32S3OpenData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

/**
 * ESP32-S3 物品的 ScreenHandler。
 * 服务端持有玩家手持物品栈的引用，客户端持有开屏包下发的数据副本。
 */
public class Esp32S3ScreenHandler extends ScreenHandler implements SenderEditorData {

    private final ItemStack stack;
    private byte module;
    private byte command;
    private byte length;
    private byte[] data = new byte[0];

    /**
     * 客户端构造：数据来自 OpenScreen 包。
     */
    public Esp32S3ScreenHandler(int syncId, PlayerInventory inventory, Esp32S3OpenData openData) {
        super(ModScreenHandlers.ESP32_S3_SCREEN_HANDLER, syncId);
        this.stack = null;
        this.module = openData.module();
        this.command = openData.command();
        this.length = openData.length();
        this.data = openData.data();
    }

    /**
     * 服务端构造：直接从物品栈读取数据。
     */
    public Esp32S3ScreenHandler(int syncId, PlayerInventory inventory, ItemStack stack) {
        super(ModScreenHandlers.ESP32_S3_SCREEN_HANDLER, syncId);
        this.stack = stack;
        Esp32S3Data itemData = Esp32S3Data.read(stack);
        this.module = itemData.module();
        this.command = itemData.command();
        this.data = itemData.data() != null ? itemData.data() : new byte[0];
        this.length = (byte) (this.data.length & 0xFF);
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public byte getModule() {
        return module;
    }

    @Override
    public byte getCommand() {
        return command;
    }

    @Override
    public byte getLength() {
        return length;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public void setModule(byte module) {
        this.module = module;
    }

    @Override
    public void setCommand(byte command) {
        this.command = command;
    }

    @Override
    public void setLength(byte length) {
        this.length = length;
    }

    @Override
    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // 客户端副本不做校验；服务端要求玩家仍然手持这个物品栈
        if (stack == null) {
            return true;
        }
        return player.getMainHandStack() == stack || player.getOffHandStack() == stack;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}