package net.vg.structurevoidable.neoforge;

import net.vg.structurevoidable.Structurevoidable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Structurevoidable.MOD_ID)
public final class StructurevoidableNeoForge {
    public StructurevoidableNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Structurevoidable.init();
    }
}
