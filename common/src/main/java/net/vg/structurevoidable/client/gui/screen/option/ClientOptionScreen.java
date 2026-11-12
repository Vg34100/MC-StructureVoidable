package net.vg.structurevoidable.client.gui.screen.option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vg.structurevoidable.config.SettingsDraft;

public class ClientOptionScreen extends StructureVoidOptionsScreen {
    private final SettingsDraft draft = new SettingsDraft();

    public ClientOptionScreen(Screen parent) {
        super(parent, Component.translatable("config.client.title"));
    }

    @Override
    protected boolean hasDraft() { return true; }

    @Override
    protected void addOptions() {
        list.addSmall(new OptionInstance<?>[]{
                ConfigOptions.bool("config.barrier.behavior", draft.barrierBehavior, value -> draft.barrierBehavior = value),
                ConfigOptions.bool("config.outline.always.visible", draft.outlineVisible, value -> draft.outlineVisible = value),
                ConfigOptions.choice("config.outline.size", "outline.size.", List.of("none", "small", "medium", "large"),
                        draft.outlineSize, value -> draft.outlineSize = value),
                ConfigOptions.bool("config.full.block.render", draft.fullBlockRender, value -> draft.fullBlockRender = value),
                ConfigOptions.choice("config.outline.color", "outline.color.", List.of("default", "void", "barrier"),
                        draft.outlineColor, value -> draft.outlineColor = value),
                ConfigOptions.bool("config.display.block", draft.displayBlock, value -> draft.displayBlock = value),
                ConfigOptions.choice("config.block.type", "block.type.", List.of("stone", "deepslate", "dirt", "netherrack", "endstone"),
                        draft.blockType, value -> draft.blockType = value)
        });
    }

    @Override
    protected void saveChanges() {
        try { draft.save(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
