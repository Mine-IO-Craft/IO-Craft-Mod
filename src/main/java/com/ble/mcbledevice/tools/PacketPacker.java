package com.ble.mcbledevice.tools;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 协议帧分块工具。
 *
 * 协议帧格式为 module(1) + command(1) + length(1) + data，
 * 每帧自带长度、可连续解析，因此可以把多个完整帧拼接后一次发送。
 * 本工具保证：
 *   1. 绝不从帧中间切开（一个数据段完整地落在同一块里）；
 *   2. 每块不超过指定字节上限（默认 244，与 ESP32 端单包上限一致）；
 *   3. 单个帧本身就超过上限时，单独成块并标记 oversized，由各通道自行判定处理。
 */
public final class PacketPacker {

    /** 单块默认上限：与固件一次能接收的数据包上限一致 */
    public static final int DEFAULT_MAX_CHUNK = 244;

    private PacketPacker() {
    }

    /** 打包结果：一块连续的字节流，以及它包含的完整帧数量 */
    public static final class Chunk {
        public final byte[] data;
        public final int frameCount;
        public final boolean oversized;

        Chunk(byte[] data, int frameCount, boolean oversized) {
            this.data = data;
            this.frameCount = frameCount;
            this.oversized = oversized;
        }
    }

    /**
     * 把若干完整协议帧按帧边界打包成若干块。
     *
     * @param frames        完整帧列表（顺序即发送顺序）
     * @param maxChunkBytes 单块字节上限
     */
    public static List<Chunk> pack(List<byte[]> frames, int maxChunkBytes) {
        int limit = Math.max(3, maxChunkBytes);
        List<Chunk> chunks = new ArrayList<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(limit);
        int framesInChunk = 0;

        for (byte[] frame : frames) {
            if (frame == null || frame.length == 0) {
                continue;
            }
            // 单帧超限：先把已攒的块输出，再让它单独成块（标记 oversized）
            if (frame.length > limit) {
                if (framesInChunk > 0) {
                    chunks.add(new Chunk(buffer.toByteArray(), framesInChunk, false));
                    buffer.reset();
                    framesInChunk = 0;
                }
                chunks.add(new Chunk(frame.clone(), 1, true));
                continue;
            }
            // 放不下就换块，保证帧完整
            if (framesInChunk > 0 && buffer.size() + frame.length > limit) {
                chunks.add(new Chunk(buffer.toByteArray(), framesInChunk, false));
                buffer.reset();
                framesInChunk = 0;
            }
            buffer.write(frame, 0, frame.length);
            framesInChunk++;
        }

        if (framesInChunk > 0) {
            chunks.add(new Chunk(buffer.toByteArray(), framesInChunk, false));
        }
        return chunks;
    }
}