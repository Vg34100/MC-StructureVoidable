package net.vg.structurevoidable.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.vg.structurevoidable.Structurevoidable;

/** Forge 47 has its own lifecycle; never substitute NeoForge classes here. */
@Mod(Structurevoidable.MOD_ID)
public final class StructurevoidableForge {
    public StructurevoidableForge() {
        EventBuses.registerModEventBus(Structurevoidable.MOD_ID,
                FMLJavaModLoadingContext.get().getModEventBus());
        Structurevoidable.init();
    }
}
