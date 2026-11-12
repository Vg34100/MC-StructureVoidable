//? if >=26.1 && <26.1.2 {
package net.vg.structurevoidable.neoforge.compat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact Architectury 20.0.4 bridge shape, not a general descriptor rewriter. */
public final class EarlyNeoForgeEventFix {
    public static final String TARGET = "dev.architectury.event.forge.EventHandlerImplCommon";
    public static final String MISSING = "net/neoforged/neoforge/event/level/block/BreakBlockEvent";
    public static final String AVAILABLE = "net/neoforged/neoforge/event/level/BlockEvent$BreakEvent";
    private static final String OLD_DESCRIPTOR = "(L" + MISSING + ";)V";
    private static final String NEW_DESCRIPTOR = "(L" + AVAILABLE + ";)V";
    private static final Map<String,Integer> EXPECTED = Map.of(
            "getPlayer()Lnet/minecraft/world/entity/player/Player;", 2,
            "getLevel()Lnet/minecraft/world/level/LevelAccessor;", 2,
            "getPos()Lnet/minecraft/core/BlockPos;", 1,
            "getState()Lnet/minecraft/world/level/block/state/BlockState;", 1,
            "setCanceled(Z)V", 1);

    private EarlyNeoForgeEventFix() {}

    /** Idempotent when another suite mod has already applied the same fix. */
    public static boolean repair(ClassNode target) {
        if (!target.name.equals(TARGET.replace('.', '/'))) throw new IllegalStateException("Unexpected ABI target");
        var oldMethods = target.methods.stream().filter(method -> method.name.equals("event") && method.desc.equals(OLD_DESCRIPTOR)).toList();
        var newMethods = target.methods.stream().filter(method -> method.name.equals("event") && method.desc.equals(NEW_DESCRIPTOR)).toList();
        if (oldMethods.isEmpty() && newMethods.size() == 1 && calls(newMethods.get(0), MISSING).isEmpty()) return false;
        if (oldMethods.size() != 1 || !newMethods.isEmpty()) throw new IllegalStateException("Unexpected Architectury block-break bridge descriptor");
        MethodNode method = oldMethods.get(0);
        // Validate BEFORE mutation. Pins/input drift must not produce a partial patch.
        if (method.signature != null || !calls(method, MISSING).equals(EXPECTED))
            throw new IllegalStateException("Unexpected Architectury 20.0.4 block-break bridge instructions");
        method.desc = NEW_DESCRIPTOR;
        if (method.localVariables != null) {
            for (var local : method.localVariables) {
                if (local.desc.equals("L" + MISSING + ";")) local.desc = "L" + AVAILABLE + ";";
            }
        }
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(MISSING)) call.owner = AVAILABLE;
            if (instruction instanceof FrameNode frame) {
                replaceFrame(frame.local);
                replaceFrame(frame.stack);
            }
        }
        return true;
    }

    private static Map<String,Integer> calls(MethodNode method, String owner) {
        var counts = new HashMap<String,Integer>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner))
                counts.merge(call.name + call.desc, 1, Integer::sum);
        }
        return counts;
    }

    private static void replaceFrame(List<Object> frame) {
        if (frame != null) frame.replaceAll(value -> MISSING.equals(value) ? AVAILABLE : value);
    }
}
//? }
