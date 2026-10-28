package ru.sloboda.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import ru.sloboda.Sloboda;
import ru.sloboda.entity.SettlerEntity;

public final class ModEntities {
    private static final ResourceKey<EntityType<?>> SETTLER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Sloboda.id("settler"));

    public static final EntityType<SettlerEntity> SETTLER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SETTLER_KEY,
            EntityType.Builder.<SettlerEntity>of(SettlerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(SETTLER_KEY));

    private ModEntities() {}

    public static void init() {}
}
