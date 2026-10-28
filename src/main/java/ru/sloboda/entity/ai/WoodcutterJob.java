package ru.sloboda.entity.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.Zone;
import ru.sloboda.colony.ZoneType;
import ru.sloboda.entity.SettlerEntity;

/** Лесоруб: находит дерево, валит его целиком, сажает саженец, носит брёвна в амбар. */
public class WoodcutterJob extends Job {
    private static final int DEFAULT_SEARCH_RADIUS = 24;
    private static final int LOGS_TO_DELIVER = 32;
    private static final int CHOP_INTERVAL = 10;
    private static final int MAX_TREE_SIZE = 128;

    private enum State { FIND, WALK, CHOP, DELIVER }

    private State state = State.FIND;
    private BlockPos target;
    private final ArrayDeque<BlockPos> treeLogs = new ArrayDeque<>();
    private final Set<BlockPos> blacklist = new HashSet<>();
    private int chopTimer;
    private long blacklistResetAt;

    public WoodcutterJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public void tick(ServerLevel level, Colony colony) {
        if (level.getGameTime() > blacklistResetAt) {
            blacklist.clear();
            blacklistResetAt = level.getGameTime() + 2400;
        }

        switch (state) {
            case FIND -> {
                if (logsCarried() >= LOGS_TO_DELIVER || inventoryFull()) {
                    state = State.DELIVER;
                    return;
                }
                status = "ищет дерево";
                target = findTree(level, colony);
                if (target == null) {
                    if (carried() > 0) {
                        state = State.DELIVER;
                    } else {
                        idle(level, 200, colony.zonesOf(ZoneType.FOREST).isEmpty()
                                ? "не видит деревьев рядом — размечай лесную зону"
                                : "в лесной зоне не осталось деревьев");
                    }
                    return;
                }
                state = State.WALK;
            }
            case WALK -> {
                status = "идёт к дереву";
                if (!level.getBlockState(target).is(BlockTags.LOGS)) {
                    state = State.FIND;
                    return;
                }
                if (moveNear(level, target, 2.5)) {
                    List<BlockPos> logs = collectTree(level, colony, target);
                    if (logs.isEmpty()) {
                        blacklist.add(target);
                        state = State.FIND;
                        return;
                    }
                    treeLogs.clear();
                    treeLogs.addAll(logs);
                    chopTimer = 0;
                    state = State.CHOP;
                }
            }
            case CHOP -> {
                status = "рубит дерево";
                lookAt(target);
                if (++chopTimer < CHOP_INTERVAL) return;
                chopTimer = 0;
                BlockPos log = treeLogs.pollFirst();
                if (log == null) {
                    replant(level, target);
                    state = (logsCarried() >= LOGS_TO_DELIVER || inventoryFull()) ? State.DELIVER : State.FIND;
                    return;
                }
                BlockState st = level.getBlockState(log);
                if (st.is(BlockTags.LOGS)) {
                    swing();
                    List<ItemStack> drops = Block.getDrops(st, level, log, null, settler, settler.getMainHandItem());
                    for (ItemStack drop : drops) {
                        ItemStack rest = settler.getInventory().addItem(drop);
                        if (!rest.isEmpty()) {
                            level.addFreshEntity(new ItemEntity(level, settler.getX(), settler.getY() + 0.5, settler.getZ(), rest));
                        }
                    }
                    level.destroyBlock(log, false);
                }
            }
            case DELIVER -> {
                if (deliver(level, colony)) {
                    takeSaplings(colony);
                    state = State.FIND;
                }
            }
        }
    }

    private int logsCarried() {
        SimpleContainer inv = settler.getInventory();
        int n = 0;
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.is(ItemTags.LOGS)) n += st.getCount();
        }
        return n;
    }

    /** Ближайшее «живое» дерево: бревно, стоящее на земле, вне изб. */
    private BlockPos findTree(ServerLevel level, Colony colony) {
        List<int[]> areas = new ArrayList<>();
        for (Zone z : colony.zonesOf(ZoneType.FOREST)) {
            areas.add(new int[]{z.minX(), z.minZ(), z.maxX(), z.maxZ()});
        }
        if (areas.isEmpty()) {
            BlockPos c = colony.center();
            areas.add(new int[]{c.getX() - DEFAULT_SEARCH_RADIUS, c.getZ() - DEFAULT_SEARCH_RADIUS,
                    c.getX() + DEFAULT_SEARCH_RADIUS, c.getZ() + DEFAULT_SEARCH_RADIUS});
        }

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] a : areas) {
            for (int x = a[0]; x <= a[2]; x++) {
                for (int z = a[1]; z <= a[3]; z++) {
                    BlockPos column = new BlockPos(x, settler.getBlockY(), z);
                    if (!level.isLoaded(column)) continue;
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos base = new BlockPos(x, h - 1, z);
                    if (!level.getBlockState(base).is(BlockTags.LOGS)) continue;
                    for (int guard = 0; guard < 40 && level.getBlockState(base.below()).is(BlockTags.LOGS); guard++) {
                        base = base.below();
                    }
                    if (!level.getBlockState(base.below()).is(BlockTags.DIRT)) continue;
                    if (blacklist.contains(base) || colony.isInHouse(base)) continue;
                    double d = settler.distanceToSqr(Vec3.atCenterOf(base));
                    if (d < bestDist) {
                        bestDist = d;
                        best = base;
                    }
                }
            }
        }
        return best;
    }

    /** Все брёвна дерева сверху вниз. Пусто, если рядом нет листвы (значит, это постройка). */
    private List<BlockPos> collectTree(ServerLevel level, Colony colony, BlockPos base) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        boolean leaves = false;

        while (!queue.isEmpty() && logs.size() < MAX_TREE_SIZE) {
            BlockPos p = queue.poll();
            if (!level.getBlockState(p).is(BlockTags.LOGS) || colony.isInHouse(p)) continue;
            logs.add(p);
            if (!leaves) {
                for (Direction d : Direction.values()) {
                    if (level.getBlockState(p.relative(d)).is(BlockTags.LEAVES)) {
                        leaves = true;
                        break;
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() < base.getY()) continue;
                        if (Math.abs(n.getX() - base.getX()) > 7 || Math.abs(n.getZ() - base.getZ()) > 7) continue;
                        if (seen.add(n)) queue.add(n);
                    }
                }
            }
        }
        if (!leaves) return List.of();
        logs.sort(Comparator.comparingInt(BlockPos::getY).reversed());
        return logs;
    }

    private void replant(ServerLevel level, BlockPos base) {
        if (!level.getBlockState(base).isAir()) return;
        SimpleContainer inv = settler.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.is(ItemTags.SAPLINGS)) {
                BlockState sapling = Block.byItem(st.getItem()).defaultBlockState();
                if (sapling.canSurvive(level, base)) {
                    level.setBlock(base, sapling, Block.UPDATE_ALL);
                    st.shrink(1);
                    swing();
                }
                return;
            }
        }
    }

    private void takeSaplings(Colony colony) {
        SimpleContainer inv = settler.getInventory();
        int have = 0;
        for (int s = 0; s < inv.getContainerSize(); s++) {
            if (inv.getItem(s).is(ItemTags.SAPLINGS)) have += inv.getItem(s).getCount();
        }
        if (have >= 2) return;
        for (ItemStack st : colony.take(s -> s.is(ItemTags.SAPLINGS), 4)) {
            ItemStack rest = inv.addItem(st);
            if (!rest.isEmpty()) colony.insert(rest);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (state == State.CHOP) state = State.FIND;
    }
}
