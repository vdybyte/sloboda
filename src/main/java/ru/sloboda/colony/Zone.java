package ru.sloboda.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Прямоугольная зона в плане (высота не учитывается). Границы включительные. */
public record Zone(ZoneType type, int minX, int minZ, int maxX, int maxZ) {
    public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
            ZoneType.CODEC.fieldOf("type").forGetter(Zone::type),
            Codec.INT.fieldOf("min_x").forGetter(Zone::minX),
            Codec.INT.fieldOf("min_z").forGetter(Zone::minZ),
            Codec.INT.fieldOf("max_x").forGetter(Zone::maxX),
            Codec.INT.fieldOf("max_z").forGetter(Zone::maxZ)
    ).apply(i, Zone::new));

    public static Zone of(ZoneType type, BlockPos a, BlockPos b) {
        return new Zone(type,
                Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
