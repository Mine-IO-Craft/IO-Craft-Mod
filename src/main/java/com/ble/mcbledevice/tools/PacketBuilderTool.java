package com.ble.mcbledevice.tools;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.ble.mcbledevice.entity.Protocol.*;

/**
 * 数据包构建工具
 */
public class PacketBuilderTool {


    public static int floatToInt(float num){
        int result = (int) num;
        return result;
    }

    public static int longToInt(long num){
        return (int) num;
    }

    /**
     * 整体打包数据包方法，修改len长为1字节
     * @param module
     * @param command
     * @param data
     * @return
     */
    public static byte[] buildPacket(byte module, byte command, byte[] data) {
        // 协议长度字段只有 1 字节（最大 255），超出部分截断，避免 (byte)data.length 溢出为负数
        if (data.length > 255) {
            data = Arrays.copyOf(data, 255);
        }
        //包头
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 1 + data.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(module);
        buffer.put(command);
        buffer.put((byte) (data.length & 0xFF));
        buffer.put(data);
        return buffer.array();
    }

    //血量
    public static byte[] buildHealthPacket(float health) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        int result = floatToInt(health);
        data.putInt(result);
        return buildPacket(MODULE_MCDATA, CMD_HEALTH, data.array());
    }

    //饱食度
    public static byte[] buildFoodPacket(int food) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(food);
        return buildPacket(MODULE_MCDATA, CMD_FOOD, data.array());
    }

    //饱和度
    public static byte[] buildSaturationPacket(float saturation) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        int a =floatToInt(saturation);
        data.putInt(a);
        return buildPacket(MODULE_MCDATA, CMD_SATURATION, data.array());
    }

    //位置信息
    public static byte[] buildPositionPacket(int x, int y, int z) {
        ByteBuffer data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(x);
        data.putInt(y);
        data.putInt(z);
        return buildPacket(MODULE_MCDATA, CMD_POSITION, data.array());
    }


    public static byte[] buildBiomePacket(int biomeId) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(biomeId);
        return buildPacket(MODULE_MCDATA, CMD_BIOME, data.array());
    }

    //经验值
    public static byte[] buildLevelPacket(int level) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(level);
        return buildPacket(MODULE_MCDATA, CMD_LEVEL, data.array());
    }

    //难度
    public static byte[] buildDifficultyPacket(int difficulty) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(difficulty);
        return buildPacket(MODULE_MCDATA, CMD_DIFFICULTY, data.array());
    }

    //游戏内时间
    public static byte[] buildTimePacket(long time) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        int a = longToInt(time);
        data.putInt(a);
        return buildPacket(MODULE_MCDATA, CMD_TIME, data.array());
    }

    //玩家速度
    public static byte[] buildSpeedPacket(float speed) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        int a =floatToInt(speed);
        data.putInt(a);
        return buildPacket(MODULE_MCDATA, CMD_SPEED, data.array());
    }

    public static byte[] buildDaysPacket(int days) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(days);
        return buildPacket(MODULE_MCDATA, CMD_DAYS, data.array());
    }

}
