package net.vg.structurevoidable.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.vg.structurevoidable.Constants;
import net.vg.structurevoidable.config.ModConfigs;

import java.util.Arrays;
import java.util.List;

public class ModKeyMaps {
    public static final KeyMapping CUSTOM_KEYMAPPING = new KeyMapping(
            "key.toggle_outline_visible",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_INSERT,
            KeyMapping.Category.MISC
    );

    public static final KeyMapping CYCLE_OUTLINE_SIZE_KEYMAPPING = new KeyMapping(
            "key.cycle_outline_size",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_I,
            KeyMapping.Category.MISC
    );

    public static final KeyMapping CYCLE_BLOCK_TYPE_KEYMAPPING = new KeyMapping(
            "key.cycle_block_type",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_O,
            KeyMapping.Category.MISC
    );

    private static final List<String> OUTLINE_SIZES = Arrays.asList("none", "small", "medium", "large");
    private static final List<String> BLOCK_TYPES = Arrays.asList("none", "default", "stone", "deepslate", "dirt", "netherrack", "endstone");

    public static void register() {
        KeyMappingRegistry.register(CUSTOM_KEYMAPPING);
        KeyMappingRegistry.register(CYCLE_OUTLINE_SIZE_KEYMAPPING);
        KeyMappingRegistry.register(CYCLE_BLOCK_TYPE_KEYMAPPING);



        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            while (CUSTOM_KEYMAPPING.consumeClick()) {
                // Do action here
                ModConfigs.OUTLINE_VISIBLE = !ModConfigs.OUTLINE_VISIBLE;
                ModConfigs.saveConfigs();
            }

            // Handle cycling outline size
            while (CYCLE_OUTLINE_SIZE_KEYMAPPING.consumeClick()) {
                cycleOutlineSize();
                ModConfigs.saveConfigs();
            }

            while (CYCLE_BLOCK_TYPE_KEYMAPPING.consumeClick()) {
                cycleBlockType();
                ModConfigs.saveConfigs();
            }

        });
    }

    private static void cycleOutlineSize() {
        // Get the current index of OUTLINE_SIZE
        int currentIndex = OUTLINE_SIZES.indexOf(ModConfigs.OUTLINE_SIZE);

        // Move to the next index, wrapping around if necessary
        int nextIndex = (currentIndex + 1) % OUTLINE_SIZES.size();
        ModConfigs.OUTLINE_SIZE = OUTLINE_SIZES.get(nextIndex);

        // Log the change (or notify the player in-game if desired)
        Constants.LOGGER.info("Outline size set to: {}", ModConfigs.OUTLINE_SIZE);
    }

    private static void cycleBlockType() {
        // Get the current index of BLOCK_TYPE
        int currentIndex = BLOCK_TYPES.indexOf(ModConfigs.BLOCK_TYPE);

        // Move to the next index, wrapping around if necessary
        int nextIndex = (currentIndex + 1) % BLOCK_TYPES.size();

        switch (nextIndex) {
            case 0: // 'none'
                ModConfigs.OUTLINE_VISIBLE = false; // Turn on outline visibility
                ModConfigs.DISPLAY_BLOCK = false; // Turn on display block
                ModConfigs.BLOCK_TYPE = "none"; // Set block type to none
                break;
            case 1: // 'default'
                ModConfigs.OUTLINE_VISIBLE = true; // Turn on outline visibility
                ModConfigs.DISPLAY_BLOCK = false; // Turn off display block
                ModConfigs.BLOCK_TYPE = "default"; // Set block type to default
                break;
            default: // All other block types
                ModConfigs.OUTLINE_VISIBLE = true; // Keep outline visibility on
                ModConfigs.DISPLAY_BLOCK = true; // Keep display block on
                ModConfigs.BLOCK_TYPE = BLOCK_TYPES.get(nextIndex); // Set to the current block type
                break;
        }

        // Log the change (or notify the player in-game if desired)
        Constants.LOGGER.info("Block display set to: {} (Display block: {}, Outline visible: {})", ModConfigs.BLOCK_TYPE, ModConfigs.DISPLAY_BLOCK, ModConfigs.OUTLINE_VISIBLE);
    }


}
