package ru.sloboda.colony;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.sloboda.item.SurveyStakeItem;
import ru.sloboda.registry.ModItems;

public final class ColonyTicker {
    private ColonyTicker() {}

    public static void onServerTick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            ColonyData data = ColonyData.getIfPresent(level);
            if (data == null) continue;

            if (t % 20 == 0) {
                List<Colony> dead = new ArrayList<>();
                for (Colony c : data.all()) {
                    if (!c.tick(level)) dead.add(c);
                }
                for (Colony c : dead) {
                    c.disband(level);
                    data.remove(c);
                }
                ColonyData.markDirty(level);
            }
            if (t % 10 == 0) {
                showZones(level, data);
            }
        }
    }

    /** Подсвечивает границы зон для игроков с Межевым колом в руке. */
    private static void showZones(ServerLevel level, ColonyData data) {
        for (ServerPlayer player : level.players()) {
            boolean holding = player.getMainHandItem().is(ModItems.SURVEY_STAKE)
                    || player.getOffhandItem().is(ModItems.SURVEY_STAKE);
            if (!holding) continue;

            for (Colony c : data.all()) {
                if (player.blockPosition().distSqr(c.center()) > (Colony.RADIUS + 32) * (Colony.RADIUS + 32)) continue;
                for (Zone z : c.zones()) {
                    SimpleParticleType type = z.type() == ZoneType.RESIDENTIAL ? ParticleTypes.WAX_ON : ParticleTypes.HAPPY_VILLAGER;
                    for (int x = z.minX(); x <= z.maxX(); x += 2) {
                        spark(level, type, x, z.minZ());
                        spark(level, type, x, z.maxZ());
                    }
                    for (int zz = z.minZ(); zz <= z.maxZ(); zz += 2) {
                        spark(level, type, z.minX(), zz);
                        spark(level, type, z.maxX(), zz);
                    }
                }
                // граница слободы — редкие искры
                for (int a = 0; a < 360; a += 6) {
                    double rad = Math.toRadians(a);
                    int x = c.center().getX() + (int) Math.round(Math.cos(rad) * Colony.RADIUS);
                    int zz = c.center().getZ() + (int) Math.round(Math.sin(rad) * Colony.RADIUS);
                    spark(level, ParticleTypes.END_ROD, x, zz);
                }
            }

            BlockPos first = SurveyStakeItem.firstCorner(player);
            if (first != null) {
                level.sendParticles(ParticleTypes.FLAME, first.getX() + 0.5, first.getY() + 1.2, first.getZ() + 0.5,
                        4, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    private static void spark(ServerLevel level, SimpleParticleType type, int x, int z) {
        BlockPos col = new BlockPos(x, 64, z);
        if (!level.isLoaded(col)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        level.sendParticles(type, x + 0.5, y + 0.3, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
