package net.vg.structurevoidable.neoforge.compat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the actual pinned library bytes without loading its unavailable event type. */
public final class EarlyNeoForgeChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    private static ClassNode original() throws Exception {
        var node = new ClassNode();
        try (var input = EarlyNeoForgeChecks.class.getClassLoader().getResourceAsStream(
                EarlyNeoForgeEventFix.TARGET.replace('.', '/') + ".class")) {
            if (input == null) throw new AssertionError("Pinned Architectury class missing");
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static byte[] bytes(ClassNode node) {
        var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
    private static byte[] bytes(MethodNode method) {
        var node = new ClassNode(); node.version = 69; node.name = "checks/Holder";
        node.superName = "java/lang/Object"; node.methods.add(method); return bytes(node);
    }
    private static MethodNode affected(ClassNode node) {
        return node.methods.stream().filter(method -> method.desc.contains(EarlyNeoForgeEventFix.MISSING)).findFirst().orElseThrow();
    }
    public static void main(String[] args) throws Exception {
        ClassNode node = original();
        MethodNode method = affected(node);
        MethodNode originalMethod = method;
        var unaffected = node.methods.stream().filter(value -> value != originalMethod).toList();
        var before = unaffected.stream().map(EarlyNeoForgeChecks::bytes).toList();
        check(EarlyNeoForgeEventFix.repair(node), "Original input was not repaired");
        check(method.desc.equals("(L" + EarlyNeoForgeEventFix.AVAILABLE + ";)V"), "Wrong descriptor");
        check(!new String(bytes(node), StandardCharsets.ISO_8859_1).contains(EarlyNeoForgeEventFix.MISSING), "Unrepaired symbolic reference");
        for (int i = 0; i < unaffected.size(); i++)
            check(Arrays.equals(before.get(i), bytes(unaffected.get(i))), "Unrelated method changed");
        byte[] repaired = bytes(node);
        check(!EarlyNeoForgeEventFix.repair(node), "Duplicate suite patch was not idempotent");
        check(Arrays.equals(repaired, bytes(node)), "Repeated patch changed bytes");

        node = original(); method = affected(node);
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(EarlyNeoForgeEventFix.MISSING)) {
                method.instructions.remove(call); break;
            }
        }
        byte[] unexpected = bytes(node);
        try { EarlyNeoForgeEventFix.repair(node); throw new AssertionError("Unexpected shape accepted"); }
        catch (IllegalStateException expected) { checks++; }
        check(Arrays.equals(unexpected, bytes(node)), "Failure partially rewrote input");
        node = original(); node.name = "unrelated/Owner";
        try { EarlyNeoForgeEventFix.repair(node); throw new AssertionError("Wrong target accepted"); }
        catch (IllegalStateException expected) { checks++; }
        System.out.println("EARLY NEOFORGE COMPAT PASS: " + checks + " checks against actual Architectury bytes");
    }
}
