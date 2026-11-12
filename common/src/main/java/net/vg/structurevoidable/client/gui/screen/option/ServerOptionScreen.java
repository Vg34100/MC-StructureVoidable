package net.vg.structurevoidable.client.gui.screen.option;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Existing empty page; no local settings are presented as server authority. */
public class ServerOptionScreen extends StructureVoidOptionsScreen {
    public ServerOptionScreen(Screen parent) {
        super(parent, Component.translatable("config.server.title"));
    }

    @Override
    protected void addOptions() {}
}
