package net.vg.structurevoidable.config;

import com.mojang.datafixers.util.Pair;
import dev.architectury.platform.Platform;
import java.io.IOException;
import net.vg.structurevoidable.Constants;

import java.util.Arrays;

public class ModConfigs {
    public static SimpleConfig CONFIG;
    private static ModConfigProvider configs;
    public static boolean BARRIER_BEHAVIOR;
    public static boolean OUTLINE_VISIBLE;

    public static boolean FULL_BLOCK_OUTLINE; // Retained for compatibility with old config files
    public static String OUTLINE_SIZE;

    public static boolean FULL_BLOCK_RENDER;
    public static String OUTLINE_COLOR;

    public static boolean DISPLAY_BLOCK;
    public static String BLOCK_TYPE;

    public static void registerConfigs() {
        Constants.LOGGER.info("Registering mod configurations.");
        // Vanilla can query the mixed-in block's shape before the loader is
        // ready. Keep class loading inert; construct the schema during mod init.
        configs = createProvider();

        CONFIG = SimpleConfig.of(SimpleConfig.versionedPath(Platform.getConfigFolder(),
                Constants.MOD_ID + "config", Constants.MOD_VERSION)).provider(configs)
                .diagnostic(Constants.LOGGER::warn).request();

        assignConfigs();
    }

    static ModConfigProvider createProvider() {
        ModConfigProvider configs = new ModConfigProvider(Constants.MOD_NAME + " Settings");
        Constants.LOGGER.debug("Creating default configuration values.");
        configs.addKeyValuePair(new Pair<>("config.barrier.behavior", true), Arrays.asList(
                "If the structure void should have the same behavior as the barrier for rendering",
                "Default: True"
        ));
        configs.addKeyValuePair(new Pair<>("config.outline.always.visible", false), Arrays.asList(
                "If the outline version of the structure void should always be visible",
                "Caution: May cause lag",
                "Default: False"
        ));
        configs.addKeyValuePair(new Pair<>("config.full.block.outline", true), Arrays.asList( // Old
                "If the breakable and placeable outline should be a full block",
                "Default: True",
                "Old"
        ));
        configs.addKeyValuePair(new Pair<>("config.outline.size", "small"), Arrays.asList(
                "The size to make the breakable and placeable outline",
                "Options include: none, small, medium, and large",
                "Default: small"
        ));

        configs.addKeyValuePair(new Pair<>("config.full.block.render", false), Arrays.asList(
                "If the visible outline render should be a full block",
                "Default: False"
        ));
        configs.addKeyValuePair(new Pair<>("config.outline.color", "default"), Arrays.asList(
                "The color of the outline render",
                "Options include: default, void, barrier",
                "Default: default"
        ));

        configs.addKeyValuePair(new Pair<>("config.display.block", false), Arrays.asList(
                "If the structure void block should display as a Minecraft block",
                "Default: False"
        ));
        configs.addKeyValuePair(new Pair<>("config.block.type", "stone"), Arrays.asList(
                "The block to display when Display as Block is enabled",
                "Options include: stone, deepslate, dirt, netherrack, and endstone",
                "Default: stone"
        ));
        return configs;
    }

    static void assignConfigs() {
        Constants.LOGGER.info("Assigning configuration values.");
        // Assign configuration values to fields
        BARRIER_BEHAVIOR = CONFIG.getOrDefault("config.barrier.behavior", true);
        OUTLINE_VISIBLE = CONFIG.getOrDefault("config.outline.always.visible", false);
        FULL_BLOCK_OUTLINE = CONFIG.getOrDefault("config.full.block.outline", true); // Old
        OUTLINE_SIZE = CONFIG.getOrDefault("config.outline.size", "small");

        FULL_BLOCK_RENDER = CONFIG.getOrDefault("config.full.block.render", false);
        OUTLINE_COLOR = CONFIG.getOrDefault("config.outline.color", "default");

        DISPLAY_BLOCK = CONFIG.getOrDefault("config.display.block", false);
        BLOCK_TYPE = CONFIG.getOrDefault("config.block.type", "stone");

        Constants.LOGGER.info("All {} configurations have been set properly.", configs.getConfigsList().size());
    }

    public static void saveConfigs() {
        try {
            new SettingsDraft().save();
        } catch (IOException | IllegalStateException failure) {
            Constants.LOGGER.error("Could not save StructureVoidable settings", failure);
        }
    }
}
