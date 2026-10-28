package ru.sloboda;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;
import ru.sloboda.colony.Zone;
import ru.sloboda.colony.ZoneType;
import ru.sloboda.entity.Profession;
import ru.sloboda.registry.ModBlocks;

/**
 * Автотест для CI: включается только с -Dsloboda.smoketest=true.
 * Основывает слободу у нуля координат, гоняет сервер несколько минут игрового времени,
 * пишет сводку в лог и останавливает сервер.
 */
public final class SmokeTest {
    private static final int DURATION_TICKS = 6000; // 5 минут игрового времени
    private static int ticks = -1;

    private SmokeTest() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(SmokeTest::setup);
        ServerTickEvents.END_SERVER_TICK.register(SmokeTest::tick);
    }

    private static void setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = -5; cx <= 5; cx++) {
            for (int cz = -5; cz <= 5; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        int y = Colony.groundY(level, 0, 0) + 1;
        BlockPos center = new BlockPos(0, y, 0);
        level.setBlock(center, ModBlocks.VECHE_STONE.defaultBlockState(), Block.UPDATE_ALL);

        Colony colony = Colony.create(center, level.getRandom(), level.getGameTime());
        ColonyData.get(level).add(colony);
        colony.giveFounderSupplies();
        colony.spawnSettler(level, Profession.BUILDER);
        colony.spawnSettler(level, Profession.WOODCUTTER);
        colony.spawnSettler(level, Profession.CARRIER);
        colony.zones().add(new Zone(ZoneType.RESIDENTIAL, 4, -20, 40, 20));
        colony.zones().add(new Zone(ZoneType.FOREST, -40, -40, -4, 40));
        ColonyData.markDirty(level);

        Sloboda.LOGGER.info("[smoketest] слобода «{}» основана в {}", colony.name(), center.toShortString());
        ticks = 0;
    }

    private static void tick(MinecraftServer server) {
        if (ticks < 0) return;
        ticks++;
        if (ticks % 600 == 0) report(server);
        if (ticks >= DURATION_TICKS) {
            ticks = -1;
            report(server);
            Sloboda.LOGGER.info("SLOBODA SMOKETEST PASSED");
            server.halt(false);
        }
    }

    private static void report(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (Colony c : ColonyData.get(level).all()) {
            for (Component line : c.statusLines(level)) {
                Sloboda.LOGGER.info("[smoketest t={}] {}", ticks, line.getString());
            }
        }
    }
}
