package net.vg.structurevoidable.config;

import com.mojang.datafixers.util.Pair;
import java.util.List;

/** One declaration owns the default, comment, validator and automatically tracked key. */
public final class ModConfigProvider implements SimpleConfig.DefaultConfig {
    private final ConfigSpec spec = new ConfigSpec();
    private final String title;

    public ModConfigProvider() { this("Mod settings"); }
    public ModConfigProvider(String title) { this.title = title; }
    @Override public ConfigSpec spec() { return spec; }
    public List<String> keys() { return spec.keys().stream().map(ConfigSpec.Key::name).toList(); }

    // Retain the suite provider API; no hand-maintained necessaryConfigs array.
    public void addKeyValuePair(Pair<String, ?> pair, List<String> comment) {
        String text = String.join("\n", comment);
        Object value = pair.getSecond();
        if (value instanceof Boolean flag) bool(pair.getFirst(), flag, text);
        else if (value instanceof Integer number) integer(pair.getFirst(), number, Integer.MIN_VALUE, Integer.MAX_VALUE, text);
        else if (value instanceof Double number) decimal(pair.getFirst(), number, -Double.MAX_VALUE, Double.MAX_VALUE, text);
        // Legacy providers accepted arbitrary printable defaults via toString().
        // Preserve that format for e.g. long/enum values; typed helpers add bounds.
        else if (value != null) spec.string(pair.getFirst(), value.toString(), text);
        else throw new IllegalArgumentException("Null config default: " + pair.getFirst());
    }

    /** The historical method returned key/comment pairs; keep that contract. */
    public List<Pair<String, List<String>>> getConfigsList() {
        return spec.keys().stream().map(key -> Pair.of(key.name(), List.of(key.comment().split("\\R")))).toList();
    }
    public ConfigSpec.Key<Boolean> bool(String key, boolean value, String comment) { return spec.bool(key, value, comment); }
    public ConfigSpec.Key<Integer> integer(String key, int value, int min, int max, String comment) {
        return spec.integer(key, value, min, max, comment);
    }
    public ConfigSpec.Key<Double> decimal(String key, double value, double min, double max, String comment) {
        return spec.decimal(key, value, min, max, comment);
    }
    @Override
    public String get(String namespace) {
        StringBuilder text = new StringBuilder();
        for (String line : title.split("\\R")) text.append("# ").append(line).append('\n');
        text.append('\n');
        for (var key : spec.keys()) {
            for (String line : key.comment().split("\\R")) text.append("# ").append(line).append('\n');
            text.append(key.name()).append('=').append(key.defaultValue()).append("\n\n");
        }
        return text.toString();
    }
}
