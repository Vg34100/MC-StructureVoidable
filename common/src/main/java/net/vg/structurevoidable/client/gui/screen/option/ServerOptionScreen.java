package net.vg.structurevoidable.client.gui.screen.option;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.vg.structurevoidable.config.ModConfigs;

public class ServerOptionScreen extends OptionsSubScreen {

    public ServerOptionScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("config.server.title"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void addOptions() {
        OptionInstance<?>[] options = new OptionInstance[]{
                // Add more options here as needed
        };

        assert this.list != null;
        this.list.addSmall(options);
    }

    @Override
    public void onClose() {
        ModConfigs.saveConfigs();
        assert this.minecraft != null;
        //? if >=26.2 {
        /*this.minecraft.gui.setScreen(this.lastScreen);
        *///? } else {
        this.minecraft.setScreen(this.lastScreen);
        //? }
    }
}
