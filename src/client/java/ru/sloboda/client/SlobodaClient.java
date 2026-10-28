package ru.sloboda.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import ru.sloboda.registry.ModEntities;

public final class SlobodaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(SettlerRenderer.LAYER, SettlerRenderer::createLayer);
        // В Fabric 26.x EntityRendererRegistry устарел: ванильный метод открыт через transitive access wideners.
        EntityRenderers.register(ModEntities.SETTLER, SettlerRenderer::new);
    }
}
