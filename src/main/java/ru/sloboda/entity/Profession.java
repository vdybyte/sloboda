package ru.sloboda.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum Profession {
    NONE("none", "Без дела", Items.AIR),
    WOODCUTTER("woodcutter", "Лесоруб", Items.STONE_AXE),
    BUILDER("builder", "Строитель", Items.STONE_SHOVEL),
    CARRIER("carrier", "Возчик", Items.BUNDLE);

    private final String id;
    private final String title;
    private final Item tool;

    Profession(String id, String title, Item tool) {
        this.id = id;
        this.title = title;
        this.tool = tool;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Item tool() { return tool; }

    public Profession next() {
        return switch (this) {
            case WOODCUTTER -> BUILDER;
            case BUILDER -> CARRIER;
            case CARRIER, NONE -> WOODCUTTER;
        };
    }

    public static Profession byId(String id) {
        for (Profession p : values()) {
            if (p.id.equals(id)) return p;
        }
        return NONE;
    }
}
