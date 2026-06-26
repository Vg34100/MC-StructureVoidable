package net.vg.structurevoidable.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.vg.structurevoidable.block.entity.StructureVoidBlockEntity;
import net.vg.structurevoidable.config.ModConfigs;

import java.util.ArrayList;
import java.util.List;

public class StructureVoidBlockEntityRenderer implements BlockEntityRenderer<StructureVoidBlockEntity, StructureVoidBlockEntityRenderer.RenderState> {

    public static class RenderState extends BlockEntityRenderState {
        public boolean outlineVisible;
        public boolean displayBlock;
        public boolean fullBlockRender;
        public String outlineColor;
        public String blockType;
        public final List<BlockPos> structureVoidPositions = new ArrayList<>();
        public Holder<Biome> biome;
        public CardinalLighting cardinalLighting;
        public LevelLightEngine lightEngine;
    }

    public StructureVoidBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(StructureVoidBlockEntity be, RenderState state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(be, state, overlay);
        state.outlineVisible = ModConfigs.OUTLINE_VISIBLE;
        state.displayBlock = ModConfigs.DISPLAY_BLOCK;
        state.fullBlockRender = ModConfigs.FULL_BLOCK_RENDER;
        state.outlineColor = ModConfigs.OUTLINE_COLOR;
        state.blockType = ModConfigs.BLOCK_TYPE;
        state.structureVoidPositions.clear();
        state.biome = null;
        state.cardinalLighting = null;
        state.lightEngine = null;

        if (be.getLevel() instanceof ClientLevel clientLevel) {
            BlockPos blockPos = be.getBlockPos();
            state.biome = clientLevel.getBiome(blockPos);
            state.cardinalLighting = clientLevel.cardinalLighting();
            state.lightEngine = clientLevel.getLightEngine();
            if (clientLevel.getBlockState(blockPos).is(Blocks.STRUCTURE_VOID)) {
                state.structureVoidPositions.add(blockPos.immutable());
            }
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (!state.outlineVisible) return;

        double distSq = state.blockPos.distToCenterSqr(cameraRenderState.pos.x, cameraRenderState.pos.y, cameraRenderState.pos.z);
        if (distSq > 128.0) return;

        if (!state.displayBlock) {
            renderOutlines(state);
        } else {
            renderDisplayBlock(state, poseStack, nodeCollector);
        }
    }

    private void renderOutlines(RenderState state) {
        int color = getColor(state.outlineColor);
        GizmoStyle style = GizmoStyle.stroke(color);

        for (BlockPos pos : state.structureVoidPositions) {
            AABB box = state.fullBlockRender
                    ? new AABB(pos)
                    : new AABB(pos).deflate(0.45);
            Gizmos.cuboid(box, style);
        }
    }

    private void renderDisplayBlock(RenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (state.cardinalLighting == null || state.lightEngine == null || state.biome == null) return;

        BlockState blockState = switch (state.blockType) {
            case "deepslate" -> Blocks.DEEPSLATE.defaultBlockState();
            case "dirt" -> Blocks.DIRT.defaultBlockState();
            case "netherrack" -> Blocks.NETHERRACK.defaultBlockState();
            case "endstone" -> Blocks.END_STONE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };

        MovingBlockRenderState movingState = new MovingBlockRenderState();
        movingState.blockState = blockState;
        movingState.biome = state.biome;
        movingState.cardinalLighting = state.cardinalLighting;
        movingState.lightEngine = state.lightEngine;

        for (BlockPos pos : state.structureVoidPositions) {
            movingState.blockPos = pos;
            movingState.randomSeedPos = pos;

            poseStack.pushPose();
            poseStack.translate(
                pos.getX() - state.blockPos.getX(),
                pos.getY() - state.blockPos.getY(),
                pos.getZ() - state.blockPos.getZ()
            );
            nodeCollector.submitMovingBlock(poseStack, movingState);
            poseStack.popPose();
        }
    }

    private static int getColor(String outlineColor) {
        return switch (outlineColor) {
            case "void" -> packARGB(0.14F, 0.70F, 0.78F);
            case "barrier" -> packARGB(1.0F, 0.0F, 0.0F);
            default -> packARGB(1.0F, 0.75F, 0.75F);
        };
    }

    private static int packARGB(float r, float g, float b) {
        return (0xFF << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }
}
