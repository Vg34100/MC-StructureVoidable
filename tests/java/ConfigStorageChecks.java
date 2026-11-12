import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import com.mojang.datafixers.util.Pair;
import net.vg.structurevoidable.config.*;

public final class ConfigStorageChecks {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void fails(Throwing action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IOException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        StarterConfigProvider provider = new StarterConfigProvider();
        Path path = root.resolve("chosen-name.properties");
        var warnings = new ArrayList<String>();
        var config = provider.open(path, warnings::add);
        check(config.get(provider.enabled), "boolean default");
        check(config.get(provider.count) == 4, "integer default");
        check(config.get(provider.scale) == 1.0, "double default");
        check(Files.readString(path).contains("example.count=4"), "declared keys automatically tracked");
        config.set(provider.count, 12);
        config.save();
        check(provider.open(path, warnings::add).get(provider.count) == 12, "explicit save/reload");
        var initial = config;
        fails(() -> initial.set(provider.count, 65), "invalid assignment accepted");
        fails(() -> initial.set(provider.scale, Double.NaN), "NaN assignment accepted");

        String original = "# Café configuration\r\nunknown=éclair # keep this\r\nexample.enabled=perhaps\r\nexample.count=-5 # invalid value retained\r\nexample.scale=NaN\r\n";
        Files.writeString(path, original, StandardCharsets.UTF_8);
        config = provider.open(path, warnings::add);
        check(Files.readString(path).equals(original), "load rewrote existing bytes");
        check(config.get(provider.enabled), "invalid boolean silently became false");
        check(config.get(provider.count) == 4 && config.get(provider.scale) == 1.0, "invalid numbers did not fall back");
        check(warnings.size() == 3, "validation diagnostics");
        config.set(provider.enabled, false);
        config.save();
        String saved = Files.readString(path);
        check(saved.contains("# Café configuration\r\nunknown=éclair # keep this\r\n"), "UTF-8/comments/unknown keys lost");
        check(saved.contains("example.count=-5 # invalid value retained\r\n"), "unedited serialized value changed");
        check(saved.contains("example.enabled=false\r\n"), "typed edit not saved");

        Path missing = root.resolve("missing-key.properties");
        Files.writeString(missing, "# Existing\nexample.enabled=false\n");
        var sparse = provider.open(missing, warnings::add);
        sparse.save();
        check(Files.readString(missing).contains("example.count=4"), "missing declared keys not appended");
        check(!sparse.get(provider.enabled), "existing value replaced by defaults");
        Files.writeString(missing, "# external edit\nexample.enabled=true\n");
        fails(sparse::save, "external edit overwritten");
        check(Files.readString(missing).startsWith("# external edit"), "external bytes changed");

        Path source = root.resolve("old-name.properties"), migrated = root.resolve("new-name.properties");
        String old = "# 保留\r\nold.count=7 # comment\r\nunknown=untouched\r\n";
        Files.writeString(source, old);
        Utf8Config.migrateToNewFile(source, migrated, Map.of("old.count", "example.count"));
        check(Files.readString(source).equals(old), "migration altered source");
        check(Files.readString(migrated).equals(old.replace("old.count", "example.count")), "migration altered values/comments/newlines");
        fails(() -> Utf8Config.migrateToNewFile(source, migrated, Map.of()), "migration overwrote destination");
        Files.writeString(source, "first=1\nsecond=2\n");
        fails(() -> Utf8Config.migrateToNewFile(source, root.resolve("collision.properties"), Map.of("first", "same", "second", "same")), "colliding migration accepted");
        check(!Files.exists(root.resolve("collision.properties")), "invalid migration wrote output");
        Files.writeString(source, "same=1\nsame=2\n");
        fails(() -> provider.open(source, warnings::add), "duplicate keys accepted");

        // Actual suite-shaped call sites, with the exact existing filename and keys.
        var legacyProvider = new ModConfigProvider("Café settings");
        legacyProvider.addKeyValuePair(Pair.of("Config.Enabled", true), List.of("Existing key spelling"));
        legacyProvider.integer("config.count", 4, 1, 64, "Existing count");
        legacyProvider.decimal("config.scale", 1.0, 0.25, 4.0, "Existing scale");
        check(legacyProvider.keys().equals(List.of("Config.Enabled", "config.count", "config.scale")), "provider key tracking/order");
        check(legacyProvider.getConfigsList().get(0).getSecond().equals(List.of("Existing key spelling")), "legacy comments contract");
        Path legacyPath = SimpleConfig.versionedPath(root, "existingconfig", "1.7.0-beta.1");
        check(legacyPath.getFileName().toString().equals("existingconfig-1.7.0-beta.1.properties"), "legacy filename changed");
        String legacyText = "# Configuración 保留\r\nConfig.Enabled=false\r\nconfig.count=12 # retain\r\nunknown=éclair=a\r\n";
        Files.writeString(legacyPath, legacyText);
        var legacy = SimpleConfig.of(legacyPath).provider(legacyProvider).diagnostic(warnings::add).request();
        check(!legacy.isBroken(), "compatible file failed to load");
        check(Files.readString(legacyPath).equals(legacyText), "legacy load rewrote bytes");
        check(!legacy.getOrDefault("Config.Enabled", true), "legacy boolean changed");
        check(legacy.getOrDefault("config.count", 4) == 12, "legacy integer changed");
        check(legacy.getOrDefault("unknown", "fallback").equals("éclair=a"), "legacy string changed");
        check(legacy.getOrDefault("config.scale", 1.0) == 1.0, "legacy missing default");
        legacy.set("config.count", 16);
        legacy.save(legacyProvider.keys().toArray(new String[0]));
        check(!legacy.isBroken(), "compatible save failed");
        check(Files.readString(legacyPath).contains("config.scale=1.0\r\n"), "missing default not appended");
        check(Files.readString(legacyPath).contains("Config.Enabled=false\r\n"), "missing key recreated config");
        check(Files.readString(legacyPath).contains("config.count=16 # retain\r\nunknown=éclair=a\r\n"), "legacy round trip lost data");
        fails(() -> legacy.set("config.count", 65), "legacy schema bounds ignored");
        fails(() -> legacy.set("config.scale", Double.POSITIVE_INFINITY), "legacy infinite assignment");
        legacy.set("added.key", "value");
        legacy.setComment("added.key", "A new comment");
        legacy.saveChecked();
        check(Files.readString(legacyPath).contains("# A new comment\r\nadded.key=value\r\n"), "legacy new key/comment not saved");
        Files.writeString(legacyPath, "Config.Enabled=invalid\nconfig.count=999\nconfig.scale=Infinity\n");
        var invalid = SimpleConfig.of(legacyPath).provider(legacyProvider).diagnostic(warnings::add).request();
        check(invalid.getOrDefault("Config.Enabled", true), "malformed legacy boolean became false");
        check(invalid.getOrDefault("config.count", 4) == 4, "legacy declared bounds not validated");
        check(invalid.getOrDefault("config.scale", 1.0) == 1.0, "non-finite legacy double accepted");
        Files.writeString(legacyPath, "# Malformed existing file\ninvalid line\n");
        var broken = SimpleConfig.of(legacyPath).provider(legacyProvider).diagnostic(warnings::add).request();
        check(broken.isBroken() && broken.getOrDefault("config.count", 4) == 4, "broken file default handling");
        fails(broken::saveChecked, "broken file overwritten");
        check(Files.readString(legacyPath).equals("# Malformed existing file\ninvalid line\n"), "broken bytes changed");
        Path customPath = root.resolve("custom-provider.properties");
        var custom = SimpleConfig.of(customPath).provider(name -> "# Legacy lambda\nOld.Key=7\n").diagnostic(warnings::add).request();
        check(custom.getOrDefault("Old.Key", 0) == 7, "custom DefaultConfig provider");
        var genericProvider = new ModConfigProvider();
        genericProvider.addKeyValuePair(Pair.of("old.long", 9000000000L), List.of("Existing printable default"));
        var generic = SimpleConfig.of(root.resolve("generic-default.properties")).provider(genericProvider).request();
        check(generic.getOrDefault("old.long", "").equals("9000000000"), "legacy generic default serialization changed");
        check(genericProvider.keys().equals(List.of("old.long")), "generic default key not tracked");
        fails(() -> SimpleConfig.versionedPath(root, "../outside", "1"), "unsafe filename allowed");
        System.out.println("CONFIG STORAGE PASS: " + checks + " checks; Java " + Runtime.version().feature());
    }
}

/** Example schema only; it creates nothing until explicitly opened by a mod. */
final class StarterConfigProvider {
    public final ConfigSpec spec = new ConfigSpec();
    public final ConfigSpec.Key<Boolean> enabled = spec.bool("example.enabled", true, "Enable the example feature");
    public final ConfigSpec.Key<Integer> count = spec.integer("example.count", 4, 1, 64, "Example count: 1 to 64");
    public final ConfigSpec.Key<Double> scale = spec.decimal("example.scale", 1.0, 0.25, 4.0, "Example scale: 0.25 to 4.0");

    public Utf8Config open(Path chosenFilename, java.util.function.Consumer<String> diagnostic) throws IOException {
        return Utf8Config.open(chosenFilename, spec, diagnostic);
    }
}
