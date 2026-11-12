package net.vg.structurevoidable.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Opt-in schema: declared defaults are the complete set of known keys. */
public final class ConfigSpec {
    private final LinkedHashMap<String, Key<?>> keys = new LinkedHashMap<>();

    public record Key<T>(String name, T defaultValue, Function<String, T> decoder,
                         Predicate<T> validator, String comment) {
        public T parse(String text) {
            T value = decoder.apply(text.strip());
            if (!validator.test(value)) throw new IllegalArgumentException("Outside allowed range");
            return value;
        }
    }

    private <T> Key<T> add(String name, T value, Function<String,T> decoder, Predicate<T> validator, String comment) {
        if (!validName(name) || keys.containsKey(name))
            throw new IllegalArgumentException("Invalid/duplicate config key: " + name);
        if (!validator.test(value)) throw new IllegalArgumentException("Invalid default: " + name);
        Key<T> key = new Key<>(name, value, decoder, validator, comment);
        keys.put(name, key);
        return key;
    }

    public Key<Boolean> bool(String name, boolean value, String comment) {
        return add(name, value, text -> {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("Expected true or false");
            return Boolean.parseBoolean(text);
        }, v -> v != null, comment);
    }

    public Key<Integer> integer(String name, int value, int min, int max, String comment) {
        return add(name, value, Integer::parseInt, v -> v != null && min <= v && v <= max, comment);
    }

    public Key<Double> decimal(String name, double value, double min, double max, String comment) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new IllegalArgumentException("Invalid decimal bounds: " + name);
        return add(name, value, Double::parseDouble,
                v -> v != null && Double.isFinite(v) && min <= v && v <= max, comment);
    }

    public Key<String> string(String name, String value, String comment) {
        return add(name, value, text -> text, v -> v != null && !v.contains("\n") && !v.contains("\r"), comment);
    }

    // Legacy suite keys can contain capitals. Keep serialized names unchanged.
    public static boolean validName(String name) {
        return name != null && name.matches("[^\\s=#!]+");
    }

    public List<Key<?>> keys() { return List.copyOf(keys.values()); }
    public boolean contains(Key<?> key) { return keys.get(key.name()) == key; }
}
