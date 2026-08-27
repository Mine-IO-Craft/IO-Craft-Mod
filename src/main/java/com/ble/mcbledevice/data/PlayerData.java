package com.ble.mcbledevice.data;

import com.ble.mcbledevice.tools.PacketBuilderTool;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import com.ble.mcbledevice.config.ModConfig;


public class PlayerData {

    public String playerName;
    public float health;
    public float maxHealth;
    public int foodLevel;
    public int saturation;          // 新增：饱和度（可选）
    public int experienceLevel;
    public int x, y, z;
    public String dimension;         // 维度名称，如 "minecraft:overworld"
    public float speed;              // 玩家速度（可选）
    public long time;                // 游戏内时间（可选）
    public int difficulty;           // 游戏难度（可选）

    //构造的时候传入一个player，然后自动获取游戏中player中的数据放入到类中
    public PlayerData(ServerPlayerEntity player) {
        this.playerName = player.getName().getString();
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getHungerManager().getFoodLevel();
        this.saturation = (int) player.getHungerManager().getSaturationLevel(); // 取整或保留浮点数
        this.experienceLevel = player.experienceLevel;
        BlockPos pos = player.getBlockPos();
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.dimension = player.getWorld().getRegistryKey().getValue().toString();
        this.speed = player.getMovementSpeed();
        this.time = player.getWorld().getTimeOfDay();
        this.difficulty = player.getWorld().getDifficulty().getId(); // 0:和平,1:简单,2:普通,3:困难
    }

    /**
     * 生成所有需要发送的数据包（每个字段一个包）
     * @return List<byte[]> 每个元素是一个完整的协议包
     * if是为了支持每一项的开启和关闭
     */
    public List<byte[]> buildAllPackets(ModConfig config) {

        List<byte[]> packets = new ArrayList<>();
        if (config.send.health) {
            packets.add(PacketBuilderTool.buildHealthPacket(health));
        }
        if (config.send.food) {
            packets.add(PacketBuilderTool.buildFoodPacket(foodLevel));
        }
        if (config.send.saturation) {
            packets.add(PacketBuilderTool.buildSaturationPacket(saturation));
        }
        if (config.send.position) {
            packets.add(PacketBuilderTool.buildPositionPacket(x, y, z));
        }
        if (config.send.level) {
            packets.add(PacketBuilderTool.buildLevelPacket(experienceLevel));
        }
        // if (config.send.biome) {
        //     packets.add(PacketBuilderTool.buildBiomePacket(getBiomeId()));
        // }
        if (config.send.difficulty) {
            packets.add(PacketBuilderTool.buildDifficultyPacket(difficulty));
        }
        if (config.send.time) {
            packets.add(PacketBuilderTool.buildTimePacket(time));
        }
        if (config.send.speed) {
            packets.add(PacketBuilderTool.buildSpeedPacket(speed));
        }
        // if (config.send.days) {
        //     packets.add(PacketBuilderTool.buildDaysPacket(getDays()));
        // }
        return packets;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PlayerData other)) return false;
        return Float.compare(other.health, health) == 0 &&
                Float.compare(other.maxHealth, maxHealth) == 0 &&
                foodLevel == other.foodLevel &&
                saturation == other.saturation &&
                experienceLevel == other.experienceLevel &&
                x == other.x && y == other.y && z == other.z &&
                dimension.equals(other.dimension) &&
                Float.compare(other.speed, speed) == 0 &&
                time == other.time &&
                difficulty == other.difficulty;
    }

    @Override
    public int hashCode() {
        int result = playerName.hashCode();
        result = 31 * result + Float.floatToIntBits(health);
        result = 31 * result + Float.floatToIntBits(maxHealth);
        result = 31 * result + foodLevel;
        result = 31 * result + saturation;
        result = 31 * result + experienceLevel;
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        result = 31 * result + dimension.hashCode();
        result = 31 * result + Float.floatToIntBits(speed);
        result = 31 * result + (int)(time ^ (time >>> 32));
        result = 31 * result + difficulty;
        return result;
    }
}