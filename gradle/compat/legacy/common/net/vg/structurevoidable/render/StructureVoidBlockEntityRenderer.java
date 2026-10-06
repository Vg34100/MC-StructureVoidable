package net.vg.structurevoidable.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.vg.structurevoidable.block.entity.StructureVoidBlockEntity;
import net.vg.structurevoidable.config.ModConfigs;

/** Legacy immediate rendering, with the canonical per-position Z-fighting fix. */
public class StructureVoidBlockEntityRenderer implements BlockEntityRenderer<StructureVoidBlockEntity> {
    public StructureVoidBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StructureVoidBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!ModConfigs.OUTLINE_VISIBLE || blockEntity.getLevel() == null) return;
        BlockPos pos = blockEntity.getBlockPos();
        if (!blockEntity.getLevel().getBlockState(pos).is(Blocks.STRUCTURE_VOID)) return;
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (pos.distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z) > 128.0) return;

        if (ModConfigs.DISPLAY_BLOCK) {
            BlockState display = switch (ModConfigs.BLOCK_TYPE) {
                case "deepslate" -> Blocks.DEEPSLATE.defaultBlockState();
                case "dirt" -> Blocks.DIRT.defaultBlockState();
                case "netherrack" -> Blocks.NETHERRACK.defaultBlockState();
                case "endstone" -> Blocks.END_STONE.defaultBlockState();
                default -> Blocks.STONE.defaultBlockState();
            };
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    display, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
            float red, green, blue;
            switch (ModConfigs.OUTLINE_COLOR) {
                case "void" -> { red = 0.14F; green = 0.70F; blue = 0.78F; }
                case "barrier" -> { red = 1.0F; green = 0.0F; blue = 0.0F; }
                default -> { red = 1.0F; green = 0.75F; blue = 0.75F; }
            }
            double min = ModConfigs.FULL_BLOCK_RENDER ? 0.0 : 0.45;
            double max = ModConfigs.FULL_BLOCK_RENDER ? 1.0 : 0.55;
            // Each BER draws only its own block, never a neighboring BER's box.
            LevelRenderer.renderLineBox(poseStack, lines, min, min, min, max, max, max,
                    red, green, blue, 1.0F);
        }
    }
}
