package net.vg.structurevoidable.config;

/*
 * Copyright © 2021 magistermaks
 * Modified by VgLevelUp 2024
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or significant portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * Compatible suite API, backed by Utf8Config. See examples/README.md for the
 * deliberate validation and non-destructive save differences from older copies.
 */

import dev.architectury.platform.Platform;
import net.vg.structurevoidable.Constants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SimpleConfig {
    @FunctionalInterface
    public interface DefaultConfig {
        String get(String namespace);
        static String empty(String namespace) { return ""; }
        // Existing providers/lambdas inherit text-based key tracking unchanged.
        default ConfigSpec spec() { return null; }
    }

    public static final class ConfigRequest {
        private final Path path;
        private DefaultConfig provider = DefaultConfig::empty;
        private Consumer<String> diagnostic = message -> System.getLogger(SimpleConfig.class.getName())
                .log(System.Logger.Level.WARNING, message);

        private ConfigRequest(Path path) { this.path = path; }
        public ConfigRequest provider(DefaultConfig provider) { this.provider = provider; return this; }
        public ConfigRequest diagnostic(Consumer<String> diagnostic) { this.diagnostic = diagnostic; return this; }
        public SimpleConfig request() { return new SimpleConfig(this); }
    }

    /** Preferred for new code: the caller owns the complete filename policy. */
    public static ConfigRequest of(Path explicitFilename) { return new ConfigRequest(explicitFilename); }

    /** Legacy suite policy: <name>-<loaded mod version>.properties. No search or migration. */
    public static ConfigRequest of(String filename) {
        return of(versionedPath(Platform.getConfigFolder(), filename,
                Platform.getMod(Constants.MOD_ID).getVersion()));
    }

    public static Path versionedPath(Path folder, String filename, String version) {
        if (!filename.matches("[a-zA-Z0-9_.-]+") || !version.matches("[a-zA-Z0-9_.+-]+"))
            throw new IllegalArgumentException("Expected filename components, not a path");
        return folder.resolve(filename + "-" + version + ".properties");
    }

    private final Consumer<String> diagnostic;
    private ConfigSpec spec;
    private Utf8Config storage;
    private boolean broken;

    private SimpleConfig(ConfigRequest request) {
        diagnostic = request.diagnostic;
        try {
            String defaults = request.provider.get(request.path.getFileName().toString());
            ConfigSpec declared = request.provider.spec();
            spec = declared == null ? parseDefaults(defaults) : declared;
            storage = Utf8Config.open(request.path, spec, defaults, diagnostic);
        } catch (IOException | RuntimeException failure) {
            broken = true;
            diagnostic.accept("Config could not be loaded; using defaults (" + failure.getClass().getSimpleName() + ")");
        }
    }

    // Custom legacy DefaultConfig lambdas also get automatic missing-key tracking.
    private static ConfigSpec parseDefaults(String text) {
        ConfigSpec spec = new ConfigSpec();
        StringBuilder comment = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.strip().startsWith("#")) comment.append(line.strip().substring(1).stripLeading()).append('\n');
            else if (!line.isBlank()) {
                int equals = line.indexOf('=');
                if (equals < 1) throw new IllegalArgumentException("Invalid default config line");
                String raw = line.substring(equals + 1);
                int inline = raw.indexOf(" #");
                spec.string(line.substring(0, equals).strip(), inline < 0 ? raw : raw.substring(0, inline), comment.toString());
                comment.setLength(0);
            }
        }
        return spec;
    }

    public boolean isBroken() { return broken; }
    public String get(String key) { return storage == null ? null : storage.raw(key); }
    public String getOrDefault(String key, String fallback) {
        String value = get(key);
        return value == null ? fallback : value;
    }

    private <T> T parsed(String name, T fallback, Function<String,T> decoder, Predicate<T> valid) {
        String raw = get(name);
        if (raw == null) return fallback;
        try {
            // Respect bounds declared in the provider even through the legacy API.
            for (var key : spec.keys()) if (key.name().equals(name)) key.parse(raw);
            T value = decoder.apply(raw.strip());
            if (valid.test(value)) return value;
        } catch (IllegalArgumentException invalid) {
            // A malformed boolean is invalid, rather than implicitly false.
        }
        diagnostic.accept("Invalid config key " + name + "; using its supplied default");
        return fallback;
    }

    public boolean getOrDefault(String key, boolean fallback) {
        return parsed(key, fallback, text -> {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("Expected boolean");
            return Boolean.parseBoolean(text);
        }, value -> true);
    }
    public int getOrDefault(String key, int fallback) {
        return getOrDefault(key, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    public int getOrDefault(String key, int fallback, int min, int max) {
        if (min > max || fallback < min || fallback > max) throw new IllegalArgumentException("Invalid integer default/range");
        return parsed(key, fallback, Integer::parseInt, value -> value >= min && value <= max);
    }
    public double getOrDefault(String key, double fallback) {
        return getOrDefault(key, fallback, -Double.MAX_VALUE, Double.MAX_VALUE);
    }
    public double getOrDefault(String key, double fallback, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max || !Double.isFinite(fallback)
                || fallback < min || fallback > max) throw new IllegalArgumentException("Invalid decimal default/range");
        return parsed(key, fallback, Double::parseDouble, value -> Double.isFinite(value) && value >= min && value <= max);
    }

    public <T> T get(ConfigSpec.Key<T> key) { return storage == null ? key.defaultValue() : storage.get(key); }
    public <T> void set(ConfigSpec.Key<T> key, T value) {
        requireLoaded();
        storage.set(key, value);
    }
    public void set(String key, String value) {
        requireLoaded();
        for (var declaration : spec.keys()) if (declaration.name().equals(key)) declaration.parse(value);
        storage.raw(key, value);
    }
    public void set(String key, boolean value) { set(key, Boolean.toString(value)); }
    public void set(String key, int value) { set(key, Integer.toString(value)); }
    public void set(String key, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Expected finite value");
        set(key, Double.toString(value));
    }
    /** Adds a comment while preserving user comments already in the file. */
    public void setComment(String key, String comment) { requireLoaded(); storage.comment(key, comment); }

    private void requireLoaded() {
        if (broken || storage == null) throw new IllegalStateException("Reload the broken config before editing");
    }
    /** Use this form when a screen must report save failure to the user. */
    public void saveChecked() throws IOException {
        if (broken || storage == null) throw new IOException("Refusing to replace an unreadable config");
        storage.save();
    }
    public void save() {
        try { saveChecked(); }
        catch (IOException failure) { broken = true; diagnostic.accept("Config save refused; reload before retrying"); }
    }
    /** Source compatibility for Combo-style callers. Provider declarations own defaults. */
    public void save(String[] requiredKeys) { save(List.of(requiredKeys)); }
    public void save(List<String> requiredKeys) {
        for (String key : requiredKeys) {
            if (get(key) == null && (spec == null || spec.keys().stream().noneMatch(value -> value.name().equals(key)))) {
                diagnostic.accept("Required key has no declared default: " + key);
                return;
            }
        }
        save();
    }
    public boolean delete() {
        if (broken || storage == null) return false;
        try { boolean deleted = storage.delete(); broken = deleted; return deleted; }
        catch (IOException failure) { diagnostic.accept("Config deletion refused"); return false; }
    }
}
