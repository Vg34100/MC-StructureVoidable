package net.vg.structurevoidable.config;

import java.io.IOException;

/** A screen edits this snapshot; runtime values change only after a successful save. */
public final class SettingsDraft {
    public boolean barrierBehavior = ModConfigs.BARRIER_BEHAVIOR;
    public boolean outlineVisible = ModConfigs.OUTLINE_VISIBLE;
    public boolean fullBlockOutline = ModConfigs.FULL_BLOCK_OUTLINE;
    public String outlineSize = ModConfigs.OUTLINE_SIZE;
    public boolean fullBlockRender = ModConfigs.FULL_BLOCK_RENDER;
    public String outlineColor = ModConfigs.OUTLINE_COLOR;
    public boolean displayBlock = ModConfigs.DISPLAY_BLOCK;
    public String blockType = ModConfigs.BLOCK_TYPE;

    public void save() throws IOException {
        SimpleConfig config = ModConfigs.CONFIG;
        config.set("config.barrier.behavior", barrierBehavior);
        config.set("config.outline.always.visible", outlineVisible);
        config.set("config.full.block.outline", fullBlockOutline);
        config.set("config.outline.size", outlineSize);
        config.set("config.full.block.render", fullBlockRender);
        config.set("config.outline.color", outlineColor);
        config.set("config.display.block", displayBlock);
        config.set("config.block.type", blockType);
        config.saveChecked();

        ModConfigs.BARRIER_BEHAVIOR = barrierBehavior;
        ModConfigs.OUTLINE_VISIBLE = outlineVisible;
        ModConfigs.FULL_BLOCK_OUTLINE = fullBlockOutline;
        ModConfigs.OUTLINE_SIZE = outlineSize;
        ModConfigs.FULL_BLOCK_RENDER = fullBlockRender;
        ModConfigs.OUTLINE_COLOR = outlineColor;
        ModConfigs.DISPLAY_BLOCK = displayBlock;
        ModConfigs.BLOCK_TYPE = blockType;
    }
}
