//? if >=26.1 && <26.1.2 {
package net.vg.structurevoidable.neoforge.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The plugin repairs the ABI before reflection links the target's event methods. */
@Mixin(targets = "dev.architectury.event.forge.EventHandlerImplCommon", remap = false)
abstract class EarlyNeoForgeAnchor {}
//? }
