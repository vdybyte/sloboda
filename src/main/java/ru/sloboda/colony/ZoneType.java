package ru.sloboda.colony;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum ZoneType implements StringRepresentable {
    RESIDENTIAL("residential", "Жилая"),
    FOREST("forest", "Лесная");

    public static final Codec<ZoneType> CODEC = StringRepresentable.fromEnum(ZoneType::values);

    private final String id;
    private final String title;

    ZoneType(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String title() {
        return title;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
