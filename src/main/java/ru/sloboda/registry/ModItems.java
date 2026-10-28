package ru.sloboda.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.sloboda.Sloboda;
import ru.sloboda.item.SurveyStakeItem;

public final class ModItems {
    public static final Item SURVEY_STAKE = register("survey_stake",
            new SurveyStakeItem(props("survey_stake").stacksTo(1)));

    public static final CreativeModeTab TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            Sloboda.id("main"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sloboda"))
                    .icon(() -> new ItemStack(ModBlocks.VECHE_STONE))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.VECHE_STONE);
                        output.accept(ModBlocks.GRANARY);
                        output.accept(SURVEY_STAKE);
                    })
                    .build());

    private ModItems() {}

    private static Item.Properties props(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Sloboda.id(name)));
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, Sloboda.id(name)), item);
    }

    public static void init() {}
}
