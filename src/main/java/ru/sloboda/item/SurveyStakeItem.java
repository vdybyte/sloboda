package ru.sloboda.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;
import ru.sloboda.colony.Zone;
import ru.sloboda.colony.ZoneType;

/**
 * Межевой кол. ПКМ по блоку — угол зоны (два клика = зона).
 * Shift + ПКМ — сменить режим: жилая → лесная → стереть.
 */
public class SurveyStakeItem extends Item {
    private enum Mode {
        RESIDENTIAL("жилая зона", ChatFormatting.GOLD),
        FOREST("лесная зона", ChatFormatting.GREEN),
        ERASE("стереть зону", ChatFormatting.RED);

        final String title;
        final ChatFormatting color;

        Mode(String title, ChatFormatting color) {
            this.title = title;
            this.color = color;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final class Selection {
        Mode mode = Mode.RESIDENTIAL;
        BlockPos first;
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    public SurveyStakeItem(Properties properties) {
        super(properties);
    }

    public static BlockPos firstCorner(Player player) {
        Selection s = SELECTIONS.get(player.getUUID());
        return s == null ? null : s.first;
    }

    private static Selection selection(Player player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), u -> new Selection());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && player.isShiftKeyDown()) {
            cycle(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (sp.isShiftKeyDown()) {
            cycle(sp);
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        Selection sel = selection(sp);
        ColonyData data = ColonyData.get(level);
        Colony colony = data.at(pos);
        if (colony == null) {
            sp.sendSystemMessage(Component.literal("Здесь нет слободы. Зоны размечаются в пределах "
                    + Colony.RADIUS + " блоков от Вечевого камня.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        if (sel.mode == Mode.ERASE) {
            boolean removed = colony.zones().removeIf(z -> z.contains(pos));
            ColonyData.markDirty(level);
            sp.sendSystemMessage(Component.literal(removed ? "Зона стёрта." : "Здесь нет зоны.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        if (sel.first == null) {
            sel.first = pos.immutable();
            sp.sendSystemMessage(Component.literal("Первый угол (" + sel.mode.title + "): "
                    + pos.getX() + ", " + pos.getZ() + ". Кликни второй угол.").withStyle(sel.mode.color), true);
            return InteractionResult.SUCCESS;
        }

        Zone zone = Zone.of(sel.mode == Mode.RESIDENTIAL ? ZoneType.RESIDENTIAL : ZoneType.FOREST, sel.first, pos);
        sel.first = null;

        if (zone.sizeX() > Colony.MAX_ZONE_SIDE || zone.sizeZ() > Colony.MAX_ZONE_SIDE) {
            sp.sendSystemMessage(Component.literal("Слишком большая зона: максимум "
                    + Colony.MAX_ZONE_SIDE + "×" + Colony.MAX_ZONE_SIDE + ".").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (zone.type() == ZoneType.RESIDENTIAL
                && (zone.sizeX() < Colony.MIN_ZONE_SIDE || zone.sizeZ() < Colony.MIN_ZONE_SIDE)) {
            sp.sendSystemMessage(Component.literal("Жилая зона должна быть не меньше "
                    + Colony.MIN_ZONE_SIDE + "×" + Colony.MIN_ZONE_SIDE + " — иначе изба не влезет.")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos farCorner = new BlockPos(zone.maxX(), pos.getY(), zone.maxZ());
        BlockPos nearCorner = new BlockPos(zone.minX(), pos.getY(), zone.minZ());
        if (!colony.contains(farCorner) || !colony.contains(nearCorner)) {
            sp.sendSystemMessage(Component.literal("Зона выходит за границу слободы.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        colony.zones().add(zone);
        ColonyData.markDirty(level);
        sp.sendSystemMessage(Component.literal("Размечена " + zone.type().title().toLowerCase() + " зона "
                + zone.sizeX() + "×" + zone.sizeZ() + " в слободе «" + colony.name() + "».")
                .withStyle(sel.mode.color));
        return InteractionResult.SUCCESS;
    }

    private static void cycle(ServerPlayer player) {
        Selection sel = selection(player);
        sel.mode = sel.mode.next();
        sel.first = null;
        player.sendSystemMessage(Component.literal("Межевой кол: " + sel.mode.title).withStyle(sel.mode.color), true);
    }
}
