package net.vg.structurevoidable.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.vg.structurevoidable.client.gui.screen.option.MainOptionScreen;
import net.vg.structurevoidable.StructureVoidableClient;
import net.vg.structurevoidable.Constants;

// The loader filters this class before linking its client-only signatures.
//? if >=26.1 {
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
//? } else {
/*@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
*///? }
public final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(StructureVoidableClient::initializeClient);
        ModList.get().getModContainerById(Constants.MOD_ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class,
                        (container, parent) -> new MainOptionScreen(parent));
    }
}
