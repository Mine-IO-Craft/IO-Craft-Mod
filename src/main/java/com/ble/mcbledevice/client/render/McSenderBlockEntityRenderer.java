package com.ble.mcbledevice.client.render;

import com.ble.mcbledevice.blockentity.McSenderBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * MC发送器方块实体渲染器：
 * 在方块四个侧面表面实时绘制 CMD / MOD / DATA。
 *
 * 当前方块没有朝向属性且为 cube_all 模型，因此把四个侧面都视为正面，
 * 每个面的文字都朝外并保持可读。DATA 超过一个可显示宽度后自动换行，
 * 换行通过 TextRenderer 实际像素宽度判断，保证一个字节不会被拆成两半。
 */
public class McSenderBlockEntityRenderer implements BlockEntityRenderer<McSenderBlockEntity> {

    public McSenderBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    /** 文字内容最多占用方块正面宽/高的比例 */
    private static final float FACE_FRACTION = 0.9F;
    /** 单个文字像素对应的方块单位上限，避免文字过大 */
    private static final float MAX_SCALE = 0.012F;
    /** DATA 标签与数据之间的水平间距（文字像素） */
    private static final int LABEL_GAP = 6;
    /** 行与行之间的额外间距（文字像素） */
    private static final int ROW_GAP = 2;
    /** CMD 行与 DATA 区块之间的间距（文字像素） */
    private static final int SECTION_GAP = 6;
    /** 从方块中心到正面外表面的偏移量（0.5 到表面，再加微小凸出避免 z-fighting） */
    private static final float FACE_OFFSET = 0.5006F;

    @Override
    public void render(McSenderBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!entity.hasWorld()) {
            return;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (entity.displayDirty || entity.displayDataRows == null) {
            rebuildLayout(entity, textRenderer);
        }
        if (entity.displayScale <= 0.0F) {
            return;
        }
        // cube_all：四个侧面都视为正面，各自朝向玩家
        renderFace(entity, matrices, vertexConsumers, textRenderer, 0.0F);
        renderFace(entity, matrices, vertexConsumers, textRenderer, 90.0F);
        renderFace(entity, matrices, vertexConsumers, textRenderer, 180.0F);
        renderFace(entity, matrices, vertexConsumers, textRenderer, -90.0F);
    }

    private void renderFace(McSenderBlockEntity entity, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, TextRenderer textRenderer,
                            float yawDegrees) {
        matrices.push();
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        // 沿旋转后的 +Z（即该面朝外）移动到表面外
        matrices.translate(0.0F, 0.0F, FACE_OFFSET);
        float scale = entity.displayScale;
        // 翻转 Y：字体坐标向下增长，映射到世界向下（-Y），保证文字方向正确
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int color = 0xFFFFFFFF;

        int lineHeight = textRenderer.fontHeight + ROW_GAP;
        int dataRowCount = entity.displayDataRows.length;
        float totalHeightPx = lineHeight + SECTION_GAP + dataRowCount * lineHeight;
        float yTop = -totalHeightPx / 2.0F;
        float xLeft = -entity.displayBlockWidth / 2.0F;

        // 第一行：CMD / MOD
        textRenderer.draw(entity.displayCmdRow, xLeft, yTop, color, true, matrix,
                vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);

        // DATA 标签与数据行（左对齐，保证字节列对齐）
        float yData = yTop + lineHeight + SECTION_GAP;
        textRenderer.draw(entity.displayDataLabel, xLeft, yData, color, true, matrix,
                vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        float xData = xLeft + entity.displayDataLabelWidth + LABEL_GAP;
        for (int i = 0; i < dataRowCount; i++) {
            textRenderer.draw(entity.displayDataRows[i], xData, yData + i * lineHeight,
                    color, true, matrix, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        matrices.pop();
    }

    /**
     * 数据变化后重建显示布局（只会发生在方块实体数据更新时，不会每帧执行）。
     * 布局结果写回方块实体缓存字段，渲染阶段只做绘制，不创建字符串。
     */
    private void rebuildLayout(McSenderBlockEntity entity, TextRenderer textRenderer) {
        byte[] data = entity.data != null ? entity.data : new byte[0];
        String cmdRow = String.format("CMD: %02X   MOD: %02X", entity.module & 0xFF, entity.command & 0xFF);
        String label = "DATA:";
        int cmdWidth = textRenderer.getWidth(cmdRow);
        int labelWidth = textRenderer.getWidth(label);
        int spaceWidth = textRenderer.getWidth(" ");

        List<String> dataRows = new ArrayList<>();
        float scale = MAX_SCALE;
        int lineHeight = textRenderer.fontHeight + ROW_GAP;

        // 迭代：先用宽度估算换行，再按行数/宽度共同约束缩小字号，直至稳定
        for (int iteration = 0; iteration < 10; iteration++) {
            float availablePx = FACE_FRACTION / scale - labelWidth - LABEL_GAP;
            if (availablePx < 24.0F) {
                availablePx = 24.0F;
            }
            dataRows.clear();
            int maxRowWidth = 0;
            StringBuilder currentRow = new StringBuilder();
            int currentWidth = 0;
            for (byte value : data) {
                String token = String.format("%02X", value & 0xFF);
                int tokenWidth = textRenderer.getWidth(token);
                int addedWidth = currentWidth == 0 ? tokenWidth : tokenWidth + spaceWidth;
                if (currentWidth > 0 && currentWidth + addedWidth > availablePx) {
                    String row = currentRow.toString();
                    dataRows.add(row);
                    maxRowWidth = Math.max(maxRowWidth, textRenderer.getWidth(row));
                    currentRow = new StringBuilder();
                    currentWidth = 0;
                    addedWidth = tokenWidth;
                }
                if (currentWidth > 0) {
                    currentRow.append(' ');
                }
                currentRow.append(token);
                currentWidth += addedWidth;
            }
            if (currentWidth > 0) {
                String row = currentRow.toString();
                dataRows.add(row);
                maxRowWidth = Math.max(maxRowWidth, textRenderer.getWidth(row));
            }
            if (dataRows.isEmpty()) {
                dataRows.add("");
            }

            int dataBlockWidth = labelWidth + LABEL_GAP + maxRowWidth;
            int blockWidth = Math.max(cmdWidth, dataBlockWidth);
            float heightPx = lineHeight + SECTION_GAP + dataRows.size() * lineHeight;
            float scaleByWidth = FACE_FRACTION / Math.max(1, blockWidth);
            float scaleByHeight = FACE_FRACTION / Math.max(1, heightPx);
            float nextScale = Math.min(scale, Math.min(scaleByWidth, scaleByHeight));
            if (Math.abs(nextScale - scale) < 0.00001F) {
                scale = nextScale;
                break;
            }
            scale = nextScale;
        }

        int maxDataRowWidth = 0;
        for (String row : dataRows) {
            maxDataRowWidth = Math.max(maxDataRowWidth, textRenderer.getWidth(row));
        }
        entity.displayCmdRow = cmdRow;
        entity.displayCmdWidth = cmdWidth;
        entity.displayDataLabel = label;
        entity.displayDataLabelWidth = labelWidth;
        entity.displayDataRows = dataRows.toArray(new String[0]);
        entity.displayDataMaxRowWidth = maxDataRowWidth;
        entity.displayBlockWidth = Math.max(cmdWidth, labelWidth + LABEL_GAP + maxDataRowWidth);
        entity.displayScale = scale;
        entity.displayDirty = false;
    }
}