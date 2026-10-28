package ru.sloboda.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.sloboda.entity.Profession;
import ru.sloboda.entity.SettlerEntity;
import ru.sloboda.registry.ModBlocks;
import ru.sloboda.registry.ModEntities;

/** Одна слобода: центр (Вечевой камень), зоны, жители, избы и общий амбар. */
public final class Colony {
    public static final int RADIUS = 64;
    public static final int MIN_DISTANCE_BETWEEN = 2 * RADIUS + 16;
    public static final int STORAGE_SIZE = 54;
    public static final int BASE_HOUSING = 2;
    public static final int HOUSING_PER_HOUSE = 2;
    public static final int ARRIVAL_TICKS = 1200; // минута
    public static final int FOOD_PER_ARRIVAL = 2;
    public static final int MAX_ZONE_SIDE = 48;
    public static final int MIN_ZONE_SIDE = 7;

    public static final Codec<Colony> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
            Codec.STRING.fieldOf("name").forGetter(c -> c.name),
            BlockPos.CODEC.fieldOf("center").forGetter(c -> c.center),
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(c -> c.zones),
            UUIDUtil.CODEC.listOf().optionalFieldOf("settlers", List.of()).forGetter(c -> c.settlers),
            HouseSite.CODEC.listOf().optionalFieldOf("houses", List.of()).forGetter(c -> c.houses),
            BlockPos.CODEC.listOf().optionalFieldOf("granaries", List.of()).forGetter(c -> c.granaries),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("storage", List.of()).forGetter(Colony::storageAsList),
            Codec.LONG.optionalFieldOf("last_arrival", 0L).forGetter(c -> c.lastArrival)
    ).apply(i, Colony::new));

    private final UUID id;
    private final String name;
    private final BlockPos center;
    private final List<Zone> zones;
    private final List<UUID> settlers;
    private final List<HouseSite> houses;
    private final List<BlockPos> granaries;
    private final SimpleContainer storage = new SimpleContainer(STORAGE_SIZE);
    private long lastArrival;

    private Colony(UUID id, String name, BlockPos center, List<Zone> zones, List<UUID> settlers,
                   List<HouseSite> houses, List<BlockPos> granaries, List<ItemStack> stored, long lastArrival) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.zones = new ArrayList<>(zones);
        this.settlers = new ArrayList<>(settlers);
        this.houses = new ArrayList<>(houses);
        this.granaries = new ArrayList<>(granaries);
        for (int s = 0; s < Math.min(stored.size(), STORAGE_SIZE); s++) {
            storage.setItem(s, stored.get(s));
        }
        this.lastArrival = lastArrival;
    }

    public static Colony create(BlockPos center, RandomSource random, long gameTime) {
        return new Colony(UUID.randomUUID(), Names.settlement(random), center.immutable(),
                List.of(), List.of(), List.of(), List.of(), List.of(), gameTime);
    }

    // ------------------------------------------------------------------ геттеры

    public UUID id() { return id; }
    public String name() { return name; }
    public BlockPos center() { return center; }
    public List<Zone> zones() { return zones; }
    public List<UUID> settlers() { return settlers; }
    public List<HouseSite> houses() { return houses; }
    public SimpleContainer storage() { return storage; }

    public boolean contains(BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    public int housing() {
        int done = 0;
        for (HouseSite h : houses) {
            if (h.done) done++;
        }
        return BASE_HOUSING + done * HOUSING_PER_HOUSE;
    }

    public int population() {
        return settlers.size();
    }

    // ------------------------------------------------------------------ амбар

    private List<ItemStack> storageAsList() {
        List<ItemStack> list = new ArrayList<>(STORAGE_SIZE);
        for (int s = 0; s < STORAGE_SIZE; s++) {
            list.add(storage.getItem(s));
        }
        return list;
    }

    /** Кладёт предмет в амбар, возвращает то, что не влезло. */
    public ItemStack insert(ItemStack stack) {
        return storage.addItem(stack);
    }

    public int count(Predicate<ItemStack> filter) {
        int total = 0;
        for (int s = 0; s < STORAGE_SIZE; s++) {
            ItemStack st = storage.getItem(s);
            if (!st.isEmpty() && filter.test(st)) total += st.getCount();
        }
        return total;
    }

    /** Списывает до max предметов по фильтру, возвращает сколько списано. */
    public int consume(Predicate<ItemStack> filter, int max) {
        int left = max;
        for (int s = 0; s < STORAGE_SIZE && left > 0; s++) {
            ItemStack st = storage.getItem(s);
            if (!st.isEmpty() && filter.test(st)) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
                if (st.isEmpty()) storage.setItem(s, ItemStack.EMPTY);
            }
        }
        storage.setChanged();
        return max - left;
    }

    /** Достаёт стопки из амбара (для инвентаря жителя). */
    public List<ItemStack> take(Predicate<ItemStack> filter, int max) {
        List<ItemStack> out = new ArrayList<>();
        int left = max;
        for (int s = 0; s < STORAGE_SIZE && left > 0; s++) {
            ItemStack st = storage.getItem(s);
            if (!st.isEmpty() && filter.test(st)) {
                out.add(st.split(Math.min(left, st.getCount())));
                left -= out.get(out.size() - 1).getCount();
                if (st.isEmpty()) storage.setItem(s, ItemStack.EMPTY);
            }
        }
        storage.setChanged();
        return out;
    }

    public static boolean isFood(ItemStack stack) {
        return stack.get(DataComponents.FOOD) != null;
    }

    /** id самого частого бревна в амбаре — из него и будет следующая изба. */
    public String mostCommonLogId() {
        Map<Item, Integer> counts = new HashMap<>();
        for (int s = 0; s < STORAGE_SIZE; s++) {
            ItemStack st = storage.getItem(s);
            if (!st.isEmpty() && st.is(ItemTags.LOGS)) counts.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        Item best = null;
        int bestCount = 0;
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null) return WoodSet.DEFAULT_LOG;
        Block block = Block.byItem(best);
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    // ------------------------------------------------------------------ амбары-блоки

    public void addGranary(BlockPos pos) {
        if (!granaries.contains(pos)) granaries.add(pos.immutable());
    }

    /** Куда нести грузы: ближайший амбар, иначе Вечевой камень. */
    public BlockPos nearestStorage(ServerLevel level, BlockPos from) {
        BlockPos best = center;
        double bestDist = from.distSqr(center);
        for (BlockPos g : granaries) {
            if (level.isLoaded(g) && level.getBlockState(g).is(ModBlocks.GRANARY)) {
                double d = from.distSqr(g);
                if (d < bestDist) {
                    bestDist = d;
                    best = g;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ зоны и избы

    public boolean isInHouse(BlockPos pos) {
        for (HouseSite h : houses) {
            if (h.footprintContains(pos)) return true;
        }
        return false;
    }

    public List<Zone> zonesOf(ZoneType type) {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones) {
            if (z.type() == type) out.add(z);
        }
        return out;
    }

    public HouseSite activeSite() {
        for (HouseSite h : houses) {
            if (!h.done) return h;
        }
        return null;
    }

    /** Ищет свободное ровное место 7×7 в жилых зонах и закладывает избу. */
    public HouseSite planSite(ServerLevel level) {
        int step = HouseSite.SIZE + 2;
        for (Zone z : zonesOf(ZoneType.RESIDENTIAL)) {
            for (int x = z.minX(); x + HouseSite.SIZE - 1 <= z.maxX(); x += step) {
                for (int zz = z.minZ(); zz + HouseSite.SIZE - 1 <= z.maxZ(); zz += step) {
                    if (!isPlotFree(x, zz)) continue;
                    int cx = x + HouseSite.SIZE / 2;
                    int cz = zz + HouseSite.SIZE / 2;
                    if (!level.isLoaded(new BlockPos(cx, center.getY(), cz))) continue;

                    int ground = groundY(level, cx, cz);
                    if (!level.getFluidState(new BlockPos(cx, ground + 1, cz)).isEmpty()) continue;
                    int min = ground, max = ground;
                    int[][] corners = {{x, zz}, {x + 6, zz}, {x, zz + 6}, {x + 6, zz + 6}};
                    for (int[] c : corners) {
                        int g = groundY(level, c[0], c[1]);
                        min = Math.min(min, g);
                        max = Math.max(max, g);
                    }
                    if (max - min > 3) continue;

                    HouseSite site = new HouseSite(new BlockPos(x, ground, zz), mostCommonLogId(), 0, 0, false);
                    houses.add(site);
                    return site;
                }
            }
        }
        return null;
    }

    private boolean isPlotFree(int x, int z) {
        for (HouseSite h : houses) {
            if (h.overlaps(x, z, 1)) return false;
        }
        List<BlockPos> special = new ArrayList<>(granaries);
        special.add(center);
        for (BlockPos p : special) {
            if (p.getX() >= x - 1 && p.getX() <= x + HouseSite.SIZE && p.getZ() >= z - 1 && p.getZ() <= z + HouseSite.SIZE) {
                return false;
            }
        }
        return true;
    }

    /** Высота твёрдой земли (без деревьев, травы и листвы). */
    public static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int guard = 0; guard < 64; guard++) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            boolean skip = st.isAir() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)
                    || (st.canBeReplaced() && st.getFluidState().isEmpty());
            if (!skip) break;
            y--;
        }
        return y;
    }

    // ------------------------------------------------------------------ жители

    public SettlerEntity spawnSettler(ServerLevel level, Profession profession) {
        SettlerEntity settler = new SettlerEntity(ModEntities.SETTLER, level);
        BlockPos pos = findSpawnPos(level);
        settler.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        settler.joinColony(id, profession, Names.person(level.getRandom()));
        level.addFreshEntity(settler);
        settlers.add(settler.getUUID());
        return settler;
    }

    private BlockPos findSpawnPos(ServerLevel level) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 12; i++) {
            int x = center.getX() + r.nextInt(9) - 4;
            int z = center.getZ() + r.nextInt(9) - 4;
            if (Math.abs(x - center.getX()) < 2 && Math.abs(z - center.getZ()) < 2) continue;
            BlockPos p = new BlockPos(x, groundY(level, x, z) + 1, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
        }
        return center.above();
    }

    /** Какое ремесло сейчас нужнее всего: лесорубов вдвое больше остальных. */
    public Profession neededProfession(ServerLevel level) {
        Map<Profession, Integer> counts = new EnumMap<>(Profession.class);
        for (UUID uuid : settlers) {
            Entity e = level.getEntity(uuid);
            if (e instanceof SettlerEntity s) counts.merge(s.getProfession(), 1, Integer::sum);
        }
        int wood = counts.getOrDefault(Profession.WOODCUTTER, 0);
        int build = counts.getOrDefault(Profession.BUILDER, 0);
        int carry = counts.getOrDefault(Profession.CARRIER, 0);
        if (build == 0) return Profession.BUILDER;
        if (wood == 0) return Profession.WOODCUTTER;
        if (carry == 0) return Profession.CARRIER;
        if (wood < 2 * build) return Profession.WOODCUTTER;
        if (carry < build) return Profession.CARRIER;
        return Profession.BUILDER;
    }

    // ------------------------------------------------------------------ жизненный цикл

    /** Вызывается раз в секунду. Возвращает false, если слободу надо распустить. */
    public boolean tick(ServerLevel level) {
        if (!level.isLoaded(center)) return true;
        if (!level.getBlockState(center).is(ModBlocks.VECHE_STONE)) return false;

        granaries.removeIf(g -> level.isLoaded(g) && !level.getBlockState(g).is(ModBlocks.GRANARY));

        long now = level.getGameTime();
        if (population() < housing() && now - lastArrival >= ARRIVAL_TICKS && count(Colony::isFood) >= FOOD_PER_ARRIVAL) {
            consume(Colony::isFood, FOOD_PER_ARRIVAL);
            SettlerEntity s = spawnSettler(level, neededProfession(level));
            lastArrival = now;
            broadcast(level, Component.literal("В слободу «" + name + "» пришёл новый житель: ")
                    .append(s.getDisplayName()).withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    public void onSettlerDied(SettlerEntity settler, ServerLevel level) {
        if (settlers.remove(settler.getUUID())) {
            broadcast(level, Component.literal("Слобода «" + name + "» потеряла жителя: ")
                    .append(settler.getDisplayName()).withStyle(ChatFormatting.RED));
        }
    }

    public void disband(ServerLevel level) {
        for (UUID uuid : settlers) {
            Entity e = level.getEntity(uuid);
            if (e instanceof SettlerEntity s) {
                s.dropInventory();
                s.discard();
            }
        }
        settlers.clear();
        Containers.dropContents(level, center, storage);
        broadcast(level, Component.literal("Слобода «" + name + "» распущена: Вечевой камень разрушен.")
                .withStyle(ChatFormatting.DARK_RED));
    }

    public void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(center) < (RADIUS + 48) * (RADIUS + 48)) {
                p.sendSystemMessage(message);
            }
        }
    }

    // ------------------------------------------------------------------ сводка

    public List<Component> statusLines(ServerLevel level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("═══ Слобода «" + name + "» ═══").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Жители: " + population() + " / " + housing() + " мест"));

        int done = 0;
        for (HouseSite h : houses) if (h.done) done++;
        lines.add(Component.literal("Избы: " + done + " готово"));

        lines.add(Component.literal("Зоны: жилых " + zonesOf(ZoneType.RESIDENTIAL).size()
                + ", лесных " + zonesOf(ZoneType.FOREST).size()));

        int logs = count(s -> s.is(ItemTags.LOGS));
        int food = count(Colony::isFood);
        int total = count(s -> true);
        lines.add(Component.literal("Амбар: брёвна " + logs + ", еда " + food + ", всего " + total + " предм."));

        HouseSite site = activeSite();
        if (site != null) {
            if (site.logsPaid < HousePlan.LOGS_NEEDED) {
                lines.add(Component.literal("Стройка: собирают брёвна " + site.logsPaid + " / " + HousePlan.LOGS_NEEDED)
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                int planSize = HousePlan.build(site.origin, WoodSet.fromLogId(site.wood)).size();
                int pct = Math.min(100, site.progress * 100 / Math.max(1, planSize));
                lines.add(Component.literal("Стройка: изба готова на " + pct + "%").withStyle(ChatFormatting.YELLOW));
            }
        } else if (zonesOf(ZoneType.RESIDENTIAL).isEmpty()) {
            lines.add(Component.literal("Нет жилой зоны — размечай её Межевым колом").withStyle(ChatFormatting.YELLOW));
        }

        if (population() >= housing()) {
            lines.add(Component.literal("Новые жители: нужно больше изб").withStyle(ChatFormatting.GRAY));
        } else if (food < FOOD_PER_ARRIVAL) {
            lines.add(Component.literal("Новые жители: нужна еда в амбаре (" + FOOD_PER_ARRIVAL + " шт.)")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            long left = Math.max(0, ARRIVAL_TICKS - (level.getGameTime() - lastArrival)) / 20;
            lines.add(Component.literal("Новый житель придёт через ~" + left + " сек.").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Стартовые припасы при основании. */
    public void giveFounderSupplies() {
        insert(new ItemStack(Items.OAK_LOG, 64));
        insert(new ItemStack(Items.BREAD, 16));
        insert(new ItemStack(Items.OAK_SAPLING, 8));
    }
}
