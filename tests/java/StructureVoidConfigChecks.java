package net.vg.structurevoidable.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Real old-file fixtures and the same draft/save path used by the options screen. */
public final class StructureVoidConfigChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static SimpleConfig open(Path path) {
        return SimpleConfig.of(path).provider(ModConfigs.createProvider()).diagnostic(message -> {}).request();
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), fixtures = Path.of(args[1]);
        Class.forName("net.vg.structurevoidable.config.ModConfigs");
        check(ModConfigs.CONFIG == null && ModConfigs.OUTLINE_SIZE == null,
                "vanilla shape queries initialized the config or loader too early");
        System.setProperty("structurevoidable.test.loaderReady", "true");
        ModConfigs.registerConfigs();
        check(Files.isRegularFile(root.resolve("config/structurevoidableconfig-1.0.3.properties")),
                "real config initialization changed the filename");
        var defaults = ModConfigs.createProvider();
        check(defaults.keys().size() == 8, "all existing keys tracked once");
        check(defaults.get("").contains("config.display.block=false\n"), "boolean display default");
        check(defaults.get("").contains("config.block.type=stone\n"), "block selection default");
        check(SimpleConfig.versionedPath(root, "structurevoidableconfig", "1.0.3").getFileName()
                .toString().equals("structurevoidableconfig-1.0.3.properties"), "filename policy changed");
        for (String fixture : List.of("legacy-default.properties", "legacy-customized.properties")) {
            Path path = root.resolve(fixture);
            byte[] original = Files.readAllBytes(fixtures.resolve(fixture));
            Files.write(path, original);
            var config = open(path);
            check(!config.isBroken(), "legacy load: " + fixture);
            check(Arrays.equals(original, Files.readAllBytes(path)), "load changed existing bytes");
            check(config.getOrDefault("config.barrier.behavior", true), "barrier value");
            if (fixture.equals("legacy-default.properties")) {
                check(!config.getOrDefault("config.display.block", false), "old typo changed effective default");
                check(config.getOrDefault("config.block.type", "stone").equals("stone"), "old missing block type");
                config.saveChecked();
                check(Files.readString(path).contains("config.display.block=stone"), "untouched typo was overwritten");
                check(Files.readString(path).contains("config.block.type=stone"), "missing declaration was not appended");
            } else {
                check(config.getOrDefault("config.outline.always.visible", false), "custom visibility lost");
                check(config.getOrDefault("config.outline.size", "small").equals("large"), "custom outline lost");
                check(config.getOrDefault("config.display.block", false), "custom display flag lost");
                check(config.getOrDefault("config.block.type", "stone").equals("deepslate"), "custom block lost");
                config.saveChecked();
                check(Arrays.equals(original, Files.readAllBytes(path)), "mixed-newline no-edit save changed bytes");
            }
        }
        Path path = root.resolve("draft.properties");
        String original = "# Café settings\r\nunknown=éclair=a # preserve\nconfig.outline.size=large\r\n"
                + "config.full.block.outline=false\nconfig.display.block=true\r\nconfig.block.type=none\n";
        Files.writeString(path, original);
        ModConfigs.CONFIG = open(path);
        ModConfigs.assignConfigs();
        var cancelled = new SettingsDraft();
        cancelled.blockType = "dirt";
        cancelled.outlineSize = "medium";
        cancelled.outlineVisible = !cancelled.outlineVisible;
        check(ModConfigs.BLOCK_TYPE.equals("none") && ModConfigs.OUTLINE_SIZE.equals("large"), "draft edited live globals");
        check(Files.readString(path).equals(original), "draft edited file before save");
        // Cancel discards the draft. A fresh screen must still show the saved values.
        var saved = new SettingsDraft();
        check(saved.blockType.equals("none") && saved.outlineSize.equals("large"), "cancel applied draft");
        saved.blockType = "dirt";
        saved.outlineSize = "medium";
        saved.outlineVisible = true;
        saved.save();
        check(ModConfigs.BLOCK_TYPE.equals("dirt") && ModConfigs.OUTLINE_SIZE.equals("medium"), "save did not apply draft");
        check(Files.readString(path).contains("unknown=éclair=a # preserve\n"), "save lost unknown UTF-8 entry");
        check(Files.readString(path).contains("config.full.block.outline=false\n"), "hidden old setting lost");
        ModConfigs.CONFIG = open(path);
        ModConfigs.assignConfigs();
        check(ModConfigs.BLOCK_TYPE.equals("dirt") && ModConfigs.OUTLINE_SIZE.equals("medium")
                && ModConfigs.OUTLINE_VISIBLE, "restart lost saved settings");
        var failed = new SettingsDraft();
        failed.blockType = "netherrack";
        String external = "# External edit\nconfig.block.type=stone\n";
        Files.writeString(path, external);
        try { failed.save(); throw new AssertionError("external edit overwritten"); }
        catch (IOException expected) { checks++; }
        check(Files.readString(path).equals(external), "failed save touched bytes");
        check(ModConfigs.BLOCK_TYPE.equals("dirt"), "failed save applied live values");
        Files.writeString(path, "# Malformed\ninvalid line\n");
        ModConfigs.CONFIG = open(path);
        ModConfigs.assignConfigs();
        check(ModConfigs.CONFIG.isBroken(), "malformed file accepted");
        ModConfigs.saveConfigs(); // Keyboard save logs failure instead of crashing/replacing input.
        check(Files.readString(path).equals("# Malformed\ninvalid line\n"), "broken keyboard save replaced file");
        System.out.println("STRUCTUREVOIDABLE CONFIG/DRAFT PASS: " + checks + " checks");
    }
}
