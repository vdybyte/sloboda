package ru.sloboda.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Участок под избу. origin — северо-западный угол на уровне пола. */
public final class HouseSite {
    public static final int SIZE = 7;

    public static final Codec<HouseSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(s -> s.origin),
            Codec.STRING.fieldOf("wood").forGetter(s -> s.wood),
            Codec.INT.optionalFieldOf("logs_paid", 0).forGetter(s -> s.logsPaid),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(s -> s.progress),
            Codec.BOOL.optionalFieldOf("done", false).forGetter(s -> s.done)
    ).apply(i, HouseSite::new));

    public final BlockPos origin;
    public final String wood;
    public int logsPaid;
    public int progress;
    public boolean done;

    public HouseSite(BlockPos origin, String wood, int logsPaid, int progress, boolean done) {
        this.origin = origin;
        this.wood = wood;
        this.logsPaid = logsPaid;
        this.progress = progress;
        this.done = done;
    }

    public BlockPos center() {
        return origin.offset(SIZE / 2, 1, SIZE / 2);
    }

    public boolean footprintContains(BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + SIZE
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + SIZE;
    }

    /** Пересекается ли прямоугольник участка (с отступом margin) с другим участком в точке x,z. */
    public boolean overlaps(int x, int z, int margin) {
        return x - margin < origin.getX() + SIZE && x + SIZE + margin > origin.getX()
                && z - margin < origin.getZ() + SIZE && z + SIZE + margin > origin.getZ();
    }
}
