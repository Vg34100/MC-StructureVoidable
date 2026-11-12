package net.vg.structurevoidable.config;

/*
 * Copyright © 2021 magistermaks
 * Modified by VgLevelUp 2024
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * with the Software without restriction, including without limitation the rights
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
 * Opt-in starter adaptation of the suite's SimpleConfig text-file approach.
 * Safe storage used by both the typed API and the compatible SimpleConfig facade.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** UTF-8 key=value files. Loading never rewrites an existing file. */
public final class Utf8Config {
    private final Path path;
    private final ConfigSpec spec;
    private final Consumer<String> diagnostic;
    private final Map<String,String> values = new LinkedHashMap<>();
    private final Map<String,String> edits = new LinkedHashMap<>();
    private final Map<String,String> comments = new LinkedHashMap<>();
    private String original;

    private Utf8Config(Path path, ConfigSpec spec, String defaultText, Consumer<String> diagnostic) throws IOException {
        this.path = path.toAbsolutePath();
        this.spec = spec;
        this.diagnostic = diagnostic;
        if (!Files.exists(this.path)) {
            Files.createDirectories(this.path.getParent());
            try {
                Files.writeString(this.path, defaultText, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException concurrentCreation) {
                // Another creator won; load its bytes without replacing them.
            }
        }
        original = Files.readString(this.path, StandardCharsets.UTF_8);
        int number = 0;
        for (String line : original.split("\\R", -1)) {
            number++;
            String key = lineKey(line);
            if (key == null) continue;
            if (values.putIfAbsent(key, rawValue(line)) != null)
                throw new IOException("Duplicate key at line " + number + ": " + key);
        }
    }

    public static Utf8Config open(Path explicitFilename, ConfigSpec spec, Consumer<String> diagnostic) throws IOException {
        StringBuilder defaults = new StringBuilder();
        for (var key : spec.keys()) appendDefault(defaults, key);
        return open(explicitFilename, spec, defaults.toString(), diagnostic);
    }

    static Utf8Config open(Path path, ConfigSpec spec, String defaults, Consumer<String> diagnostic) throws IOException {
        return new Utf8Config(path, spec, defaults, diagnostic);
    }

    private static String lineKey(String line) throws IOException {
        String stripped = line.strip();
        if (stripped.isEmpty() || stripped.startsWith("#")) return null;
        int equals = line.indexOf('=');
        if (equals < 1) throw new IOException("Expected key=value line");
        return line.substring(0, equals).strip();
    }

    private static String rawValue(String line) {
        String value = line.substring(line.indexOf('=') + 1);
        int comment = value.indexOf(" #");
        return comment < 0 ? value : value.substring(0, comment);
    }

    private static void appendDefault(StringBuilder text, ConfigSpec.Key<?> key) {
        for (String line : key.comment().split("\\R")) text.append("# ").append(line).append('\n');
        text.append(key.name()).append('=').append(key.defaultValue()).append('\n');
    }

    /** Raw access for legacy SimpleConfig call sites; typed callers use get(Key). */
    String raw(String key) { return edits.getOrDefault(key, values.get(key)); }

    void raw(String key, String value) {
        if (!ConfigSpec.validName(key) || value == null || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("Invalid config assignment: " + key);
        edits.put(key, value);
    }

    // Explicitly add a comment, retaining original user comments.
    void comment(String key, String comment) {
        if (!ConfigSpec.validName(key) || comment == null) throw new IllegalArgumentException("Invalid comment key");
        comments.put(key, comment);
    }

    private void known(ConfigSpec.Key<?> key) {
        if (!spec.contains(key)) throw new IllegalArgumentException("Undeclared config key: " + key.name());
    }

    public <T> T get(ConfigSpec.Key<T> key) {
        known(key);
        String raw = edits.getOrDefault(key.name(), values.get(key.name()));
        if (raw == null) return key.defaultValue();
        try {
            return key.parse(raw);
        } catch (IllegalArgumentException invalid) {
            diagnostic.accept("Invalid config key " + key.name() + "; using its declared default");
            return key.defaultValue();
        }
    }

    public <T> void set(ConfigSpec.Key<T> key, T value) {
        known(key);
        if (!key.validator().test(value)) throw new IllegalArgumentException("Invalid setting: " + key.name());
        edits.put(key.name(), value.toString());
    }

    /** Explicit save changes edited keys and appends missing declared defaults.
     * Unknown keys, comments and unedited serialized values remain intact. */
    public void save() throws IOException {
        if (!Files.readString(path, StandardCharsets.UTF_8).equals(original))
            throw new IOException("Config changed externally; reload before saving");
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder text = new StringBuilder();
        var seen = new HashSet<String>();
        String[] lines = original.split("(?<=\\n)", -1);
        int count = lines.length - (original.endsWith("\n") ? 1 : 0);
        for (int i=0; i<count; i++) {
            String segment = lines[i];
            String ending = segment.endsWith("\r\n") ? "\r\n" : segment.endsWith("\n") ? "\n" : "";
            String line = segment.substring(0, segment.length() - ending.length());
            String key = lineKey(line);
            if (key != null) {
                seen.add(key);
                appendComment(text, key, newline);
                if (edits.containsKey(key)) {
                    int equals = line.indexOf('=');
                    String tail = line.substring(equals+1);
                    int comment = tail.indexOf(" #");
                    line = line.substring(0, equals+1) + edits.get(key) + (comment < 0 ? "" : tail.substring(comment));
                }
            }
            text.append(line).append(ending);
        }
        for (var key : spec.keys()) {
            if (!seen.contains(key.name())) {
                seen.add(key.name());
                if (!text.isEmpty() && text.charAt(text.length()-1) != '\n') text.append(newline);
                for (String comment : key.comment().split("\\R")) text.append("# ").append(comment).append(newline);
                appendComment(text, key.name(), newline);
                text.append(key.name()).append('=').append(edits.getOrDefault(key.name(), key.defaultValue().toString())).append(newline);
            }
        }
        for (var edit : edits.entrySet()) {
            if (seen.add(edit.getKey())) {
                if (!text.isEmpty() && text.charAt(text.length()-1) != '\n') text.append(newline);
                appendComment(text, edit.getKey(), newline);
                text.append(edit.getKey()).append('=').append(edit.getValue()).append(newline);
            }
        }
        Path temporary = Files.createTempFile(path.getParent(), ".config-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        original = text.toString();
        values.putAll(edits);
        edits.clear();
        comments.clear();
    }

    private void appendComment(StringBuilder text, String key, String newline) {
        if (comments.containsKey(key)) {
            for (String line : comments.get(key).split("\\R"))
                text.append(line.startsWith("#") ? line : "# " + line).append(newline);
        }
    }

    boolean delete() throws IOException {
        if (!Files.readString(path, StandardCharsets.UTF_8).equals(original))
            throw new IOException("Config changed externally; refusing deletion");
        return Files.deleteIfExists(path);
    }

    /** Explicit, non-destructive key rename into a NEW filename. The caller
     * chooses paths/renames; values and comments are copied without conversion. */
    public static void migrateToNewFile(Path source, Path destination, Map<String,String> renames) throws IOException {
        String original = Files.readString(source, StandardCharsets.UTF_8);
        List<String> converted = new ArrayList<>();
        var seen = new HashSet<String>();
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        for (String line : original.split("\\r?\\n", -1)) {
            String key = lineKey(line);
            if (key != null) {
                String replacement = renames.getOrDefault(key, key);
                if (!ConfigSpec.validName(replacement) || !seen.add(replacement))
                    throw new IOException("Invalid/colliding migration key: " + replacement);
                if (!replacement.equals(key)) line = replacement + line.substring(line.indexOf('='));
            }
            converted.add(line);
        }
        Files.writeString(destination, String.join(newline, converted), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
