package com.ble.mcbledevice.tools;

import java.util.Arrays;

/**
 * 发送器帧的共用构建与投递逻辑。
 *
 * 方块发送器（红石触发）与 ESP32-S3 手持物品（界面发送按钮）都走这里：
 * 组出 module + command + length + data 的完整帧，再交给批次收集器统一投递。
 */
public final class SenderPacketDispatcher {

    private SenderPacketDispatcher() {
    }

    public static void dispatch(byte module, byte command, byte length, byte[] data) {
        if (data == null) {
            return;
        }
        int len = length & 0xFF;
        if (len > data.length) {
            len = data.length;
        }
        byte[] frame = PacketBuilderTool.buildPacket(
                module,
                command,
                Arrays.copyOf(data, len)
        );
        PacketBatchCollector.enqueueFrame(frame);
    }
}