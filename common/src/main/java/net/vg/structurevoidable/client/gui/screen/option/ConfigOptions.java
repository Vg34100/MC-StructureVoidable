package net.vg.structurevoidable.client.gui.screen.option;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

/** Vanilla settings controls bound to a draft, independent of the storage engine. */
final class ConfigOptions {
    private ConfigOptions() {}

    private static Component tooltip(String key) {
        return Component.translatable("tooltip." + key.substring("config.".length()));
    }

    static OptionInstance<Boolean> bool(String key, boolean value, Consumer<Boolean> changed) {
        return OptionInstance.createBoolean(key, OptionInstance.cachedConstantTooltip(tooltip(key)), value, changed::accept);
    }

    static OptionInstance<String> choice(String key, String translationPrefix, List<String> values,
                                         String value, Consumer<String> changed) {
        var choices = new ArrayList<>(values);
        // The O key also saves "none"/"default". Opening settings must retain
        // those states (or other existing serialized values) until explicitly edited.
        if (!choices.contains(value)) choices.add(0, value);
        return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(tooltip(key)),
                (label, selected) -> Component.translatable(translationPrefix + selected),
                new OptionInstance.Enum<>(List.copyOf(choices), Codec.STRING), value, changed::accept);
    }
}
