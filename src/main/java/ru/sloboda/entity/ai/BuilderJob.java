package ru.sloboda.entity.ai;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;
import ru.sloboda.colony.HousePlan;
import ru.sloboda.colony.HouseSite;
import ru.sloboda.colony.WoodSet;
import ru.sloboda.colony.ZoneType;
import ru.sloboda.entity.SettlerEntity;

/** Строитель: закладывает избу в жилой зоне, забирает брёвна из амбара и строит блок за блоком. */
public class BuilderJob extends Job {
    private static final int PLACE_INTERVAL = 4;
    private static final double BUILD_RANGE = 10.0;

    private HouseSite cachedSite;
    private List<HousePlan.Step> plan;
    private int placeTimer;

    public BuilderJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public void tick(ServerLevel level, Colony colony) {
        HouseSite site = colony.activeSite();
        if (site == null) {
            if (colony.zonesOf(ZoneType.RESIDENTIAL).isEmpty()) {
                idle(level, 200, "ждёт, пока разметят жилую зону");
                return;
            }
            site = colony.planSite(level);
            if (site == null) {
                idle(level, 400, "в жилых зонах нет ровного места 7×7");
                return;
            }
            ColonyData.markDirty(level);
        }

        // 1. Материалы.
        if (site.logsPaid < HousePlan.LOGS_NEEDED) {
            status = "собирает брёвна на избу: " + site.logsPaid + " / " + HousePlan.LOGS_NEEDED;
            BlockPos storage = colony.nearestStorage(level, settler.blockPosition());
            if (moveNear(level, storage, 3.0)) {
                int got = colony.consume(s -> s.is(ItemTags.LOGS), HousePlan.LOGS_NEEDED - site.logsPaid);
                site.logsPaid += got;
                if (got > 0) {
                    swing();
                    ColonyData.markDirty(level);
                }
                if (site.logsPaid < HousePlan.LOGS_NEEDED) {
                    idle(level, 100, "ждёт брёвен: не хватает " + (HousePlan.LOGS_NEEDED - site.logsPaid));
                }
            }
            return;
        }

        // 2. Стройка.
        if (cachedSite != site || plan == null) {
            cachedSite = site;
            plan = HousePlan.build(site.origin, WoodSet.fromLogId(site.wood));
        }
        if (site.progress >= plan.size()) {
            finish(level, colony, site);
            return;
        }

        BlockPos center = site.center();
        boolean atCenter = moveNear(level, center, 1.5);
        if (!atCenter && settler.distanceToSqr(Vec3.atBottomCenterOf(center)) > BUILD_RANGE * BUILD_RANGE) {
            status = "идёт на стройку";
            return;
        }
        status = "строит избу: " + (site.progress * 100 / plan.size()) + "%";
        if (++placeTimer < PLACE_INTERVAL) return;
        placeTimer = 0;
        placeNext(level, site);
    }

    private void placeNext(ServerLevel level, HouseSite site) {
        int skipped = 0;
        while (site.progress < plan.size() && skipped < 32) {
            HousePlan.Step step = plan.get(site.progress);
            BlockPos pos = step.pos();
            if (!level.isLoaded(pos)) return;
            BlockState current = level.getBlockState(pos);
            BlockState wanted = step.state();

            boolean done;
            if (step.onlyIfEmpty()) {
                done = !(current.isAir() || current.canBeReplaced());
            } else if (wanted.isAir()) {
                done = current.isAir();
            } else {
                done = HousePlan.sameBlock(current, wanted);
            }
            if (done) {
                site.progress++;
                skipped++;
                continue;
            }

            // Не замуровать кого-нибудь в стене.
            if (!wanted.getCollisionShape(level, pos).isEmpty()) {
                List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos));
                if (!inside.isEmpty()) {
                    if (inside.contains(settler)) {
                        BlockPos c = site.center();
                        settler.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                    } else {
                        status = "ждёт: кто-то стоит на месте стены";
                    }
                    return;
                }
            }

            lookAt(pos);
            swing();
            if (wanted.isAir()) {
                if (current.getFluidState().isEmpty()) {
                    level.destroyBlock(pos, false);
                } else {
                    level.setBlock(pos, wanted, Block.UPDATE_ALL);
                }
            } else if (HousePlan.isDoorLower(wanted)) {
                level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
                level.setBlock(pos.above(), wanted.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, Block.updateFromNeighbourShapes(wanted, level, pos), Block.UPDATE_ALL);
                playPlaceSound(level, pos, wanted);
            }
            site.progress++;
            return;
        }
    }

    private void playPlaceSound(ServerLevel level, BlockPos pos, BlockState state) {
        var sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private void finish(ServerLevel level, Colony colony, HouseSite site) {
        site.done = true;
        cachedSite = null;
        plan = null;
        ColonyData.markDirty(level);
        colony.broadcast(level, Component.literal("В слободе «" + colony.name() + "» срублена новая изба! Мест: "
                + colony.housing()).withStyle(ChatFormatting.AQUA));
        idle(level, 40, "закончил избу");
    }
}
