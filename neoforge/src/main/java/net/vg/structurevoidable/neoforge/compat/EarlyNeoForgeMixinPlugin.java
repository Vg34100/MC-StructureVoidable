//? if >=26.1 && <26.1.2 {
package net.vg.structurevoidable.neoforge.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Loaded only by the two affected targets' mixin resource. ASM/Mixin come from the loader. */
public final class EarlyNeoForgeMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!EarlyNeoForgeEventFix.TARGET.equals(targetClassName)) return;
        // A newer compatible loader can expose the original event. Leave it alone.
        if (exists(EarlyNeoForgeEventFix.MISSING)) return;
        if (!exists(EarlyNeoForgeEventFix.AVAILABLE)) throw new IllegalStateException("No supported NeoForge block-break event");
        EarlyNeoForgeEventFix.repair(targetClass);
    }

    private static boolean exists(String internalName) {
        try { Class.forName(internalName.replace('/', '.'), false, EarlyNeoForgeMixinPlugin.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }
}
//? }
