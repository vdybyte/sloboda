package ru.sloboda;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sloboda.colony.ColonyTicker;
import ru.sloboda.entity.SettlerEntity;
import ru.sloboda.registry.ModAttachments;
import ru.sloboda.registry.ModBlocks;
import ru.sloboda.registry.ModEntities;
import ru.sloboda.registry.ModItems;

public final class Sloboda implements ModInitializer {
    public static final String MOD_ID = "sloboda";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModAttachments.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();

        FabricDefaultAttributeRegistry.register(ModEntities.SETTLER, SettlerEntity.createAttributes());

        ServerTickEvents.END_SERVER_TICK.register(ColonyTicker::onServerTick);

        if (Boolean.getBoolean("sloboda.smoketest")) {
            SmokeTest.register();
        }

        LOGGER.info("Слобода загружена");
    }
}
