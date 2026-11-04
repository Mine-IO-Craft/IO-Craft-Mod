package com.ble.mcbledevice.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * ESP32-S3 物品的数据存储。
 *
 * 数据保存在物品栈的 minecraft:custom_data 组件中，字段与发送器方块实体一致：
 * Module / Command / Length / Data，仍然是原始协议帧的组成部分。
 * 物品被复制、掉落、放入箱子时数据随物品一起保存。
 */
public record Esp32S3Data(byte module, byte command, byte length, byte[] data) {

    private static final String KEY_MODULE = "Module";
    private static final String KEY_COMMAND = "Command";
    private static final String KEY_LENGTH = "Length";
    private static final String KEY_DATA = "Data";

    public static Esp32S3Data read(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return new Esp32S3Data((byte) 0, (byte) 0, (byte) 0, new byte[0]);
        }
        NbtCompound nbt = component.copyNbt();
        byte module = nbt.getByte(KEY_MODULE);
        byte command = nbt.getByte(KEY_COMMAND);
        byte length = nbt.getByte(KEY_LENGTH);
        byte[] data = nbt.getByteArray(KEY_DATA);
        return new Esp32S3Data(module, command, length, data);
    }

    public static void write(ItemStack stack, byte module, byte command, byte length, byte[] data) {
        byte[] safeData = data != null ? data : new byte[0];
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putByte(KEY_MODULE, module);
            nbt.putByte(KEY_COMMAND, command);
            nbt.putByte(KEY_LENGTH, length);
            nbt.putByteArray(KEY_DATA, safeData);
        });
    }
}