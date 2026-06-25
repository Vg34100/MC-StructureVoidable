package net.vg.structurevoidable.neoforge;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.vg.structurevoidable.StructureVoidableClient;
import net.vg.structurevoidable.Structurevoidable;
import net.neoforged.fml.common.Mod;
import net.vg.structurevoidable.client.gui.screen.option.MainOptionScreen;

@Mod(Structurevoidable.MOD_ID)
public final class StructurevoidableNeoForge {
    public StructurevoidableNeoForge(IEventBus modEventBus) {
        // Run our common setup.
        Structurevoidable.init();

        modEventBus.addListener(this::clientSetup);

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> new IConfigScreenFactory() {
            @Override
            public Screen createScreen(ModContainer modContainer, Screen arg) {
                return new MainOptionScreen(arg);
            }
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // This is called for client-side initialization
        event.enqueueWork(StructureVoidableClient::initializeClient);
    }
}
