package net.vg.structurevoidable;

import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.vg.structurevoidable.block.entity.ModBlockEntities;
import net.vg.structurevoidable.render.StructureVoidBlockEntityRenderer;

public class StructureVoidableClient {

    /**
     * This method is called to initialize client-specific components.
     * It sets up client-side logic, such as rendering.
     */
    public static void initializeClient() {
        // Log the initialization start
        Constants.LOGGER.info("Initializing client-side components for Structure Voidable...");

        // Register the block entity renderer for the Structure Void Block Entity
        Constants.LOGGER.info("Registering Structure Void Block Entity Renderer...");
        BlockEntityRendererRegistry.register(
                ModBlockEntities.STRUCTURE_VOID_BLOCK_ENTITY.get(),
                StructureVoidBlockEntityRenderer::new
        );
        // Log the successful initialization
        Constants.LOGGER.info("Client-side components initialized for Structure Voidable");
    }
}