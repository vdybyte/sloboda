package ru.sloboda.colony;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ru.sloboda.registry.ModAttachments;

/** Все слободы одного измерения. */
public final class ColonyData {
    public static final Codec<ColonyData> CODEC =
            Colony.CODEC.listOf().xmap(ColonyData::new, d -> d.colonies);

    private final List<Colony> colonies;

    public ColonyData() {
        this(new ArrayList<>());
    }

    private ColonyData(List<Colony> colonies) {
        this.colonies = new ArrayList<>(colonies);
    }

    public static ColonyData get(ServerLevel level) {
        return level.getAttachedOrCreate(ModAttachments.COLONIES);
    }

    public static ColonyData getIfPresent(ServerLevel level) {
        return level.getAttached(ModAttachments.COLONIES);
    }

    /** Сообщает Fabric, что данные изменились и их нужно сохранить. */
    public static void markDirty(ServerLevel level) {
        level.setAttached(ModAttachments.COLONIES, get(level));
    }

    public List<Colony> all() {
        return colonies;
    }

    public void add(Colony colony) {
        colonies.add(colony);
    }

    public void remove(Colony colony) {
        colonies.remove(colony);
    }

    public Colony byId(UUID id) {
        if (id == null) {
            return null;
        }
        for (Colony c : colonies) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public Colony byCenter(BlockPos center) {
        for (Colony c : colonies) {
            if (c.center().equals(center)) {
                return c;
            }
        }
        return null;
    }

    /** Слобода, в чьих границах находится точка. */
    public Colony at(BlockPos pos) {
        Colony best = null;
        double bestDist = Double.MAX_VALUE;
        for (Colony c : colonies) {
            if (c.contains(pos)) {
                double d = c.center().distSqr(pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }
        }
        return best;
    }

    /** Есть ли слобода ближе, чем minDistance (по горизонтали). */
    public boolean anyWithin(BlockPos pos, int minDistance) {
        for (Colony c : colonies) {
            int dx = c.center().getX() - pos.getX();
            int dz = c.center().getZ() - pos.getZ();
            if (dx * dx + dz * dz < minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }
}
