package com.ble.mcbledevice.tools;

import com.ble.mcbledevice.handler.BleHandler;
import com.ble.mcbledevice.handler.SerialHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块数据批次收集器。
 *
 * 发送器被红石触发时不再逐帧直接发送，而是把完整帧交给本收集器；
 * 每个服务端 tick 结束时统一冲刷：同一 tick 内产生的所有帧打包后
 * 一次性投递给串口 / TCP / BLE 三条通道（各通道按自身上限再次分块）。
 *
 * 注意：周期玩家数据不经过这里，仍走各自的单帧发送路径。
 */
public final class PacketBatchCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketBatchCollector.class);
    private static final Object LOCK = new Object();

    private static final List<byte[]> PENDING = new ArrayList<>();
    private static long pendingBytes = 0;
    private static long flushedBatches = 0;
    private static long flushedFrames = 0;

    private PacketBatchCollector() {
    }

    /** 收集一个完整协议帧（服务端线程调用） */
    public static void enqueueFrame(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return;
        }
        synchronized (LOCK) {
            PENDING.add(frame);
            pendingBytes += frame.length;
        }
    }

    /** 冲刷当前批次（每个服务端 tick 结束时调用一次） */
    public static void flush() {
        List<byte[]> frames;
        int bytes;
        synchronized (LOCK) {
            if (PENDING.isEmpty()) {
                return;
            }
            frames = new ArrayList<>(PENDING);
            bytes = (int) pendingBytes;
            PENDING.clear();
            pendingBytes = 0;
            flushedBatches++;
            flushedFrames += frames.size();
        }

        LOGGER.debug("[BATCH] 冲刷 {} 帧 / {} 字节（累计批次 {}、帧 {}）",
                frames.size(), bytes, flushedBatches, flushedFrames);
        SerialHandler.sendFrames(frames);
        NetworkHandler.sendFrames(frames);
        BleHandler.sendFrames(frames);
    }

    /** 当前待发送帧数（供界面显示） */
    public static int getPendingFrames() {
        synchronized (LOCK) {
            return PENDING.size();
        }
    }

    /** 已冲刷的批次数 */
    public static long getFlushedBatches() {
        return flushedBatches;
    }

    /** 已冲刷的帧总数 */
    public static long getFlushedFrames() {
        return flushedFrames;
    }
}