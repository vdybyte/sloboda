package ru.sloboda.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;
import ru.sloboda.entity.Profession;

/** Вечевой камень: центр слободы. Поставил — основал, разбил — распустил. */
public class VecheStoneBlock extends Block {
    public VecheStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl)) return;
        ColonyData data = ColonyData.get(sl);

        if (data.anyWithin(pos, Colony.MIN_DISTANCE_BETWEEN)) {
            level.destroyBlock(pos, true);
            if (placer instanceof ServerPlayer p) {
                p.sendSystemMessage(Component.literal("Слишком близко к другой слободе — отойди хотя бы на "
                        + Colony.MIN_DISTANCE_BETWEEN + " блоков.").withStyle(ChatFormatting.RED));
            }
            return;
        }

        Colony colony = Colony.create(pos, sl.getRandom(), sl.getGameTime());
        data.add(colony);
        colony.giveFounderSupplies();
        colony.spawnSettler(sl, Profession.BUILDER);
        colony.spawnSettler(sl, Profession.WOODCUTTER);
        ColonyData.markDirty(sl);

        if (placer instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("Основана слобода «" + colony.name() + "»!").withStyle(ChatFormatting.GOLD));
            p.sendSystemMessage(Component.literal("1) Возьми Межевой кол и размечай жилую зону (ПКМ по двум углам)."));
            p.sendSystemMessage(Component.literal("2) Shift + ПКМ колом — сменить тип зоны (жилая / лесная / стереть)."));
            p.sendSystemMessage(Component.literal("3) Амбар — общий склад. Shift + ПКМ по камню тоже открывает склад; там уже лежат стартовые припасы."));
            p.sendSystemMessage(Component.literal("ПКМ по камню — сводка слободы.").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Colony colony = ColonyData.get(sl).byCenter(pos);
            if (colony == null) {
                sp.sendSystemMessage(Component.literal("Этот камень не связан со слободой. Разбей и поставь заново.")
                        .withStyle(ChatFormatting.RED));
            } else if (sp.isShiftKeyDown()) {
                sp.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> ChestMenu.sixRows(id, inventory, colony.storage()),
                        Component.literal("Амбар слободы «" + colony.name() + "»")));
            } else {
                for (Component line : colony.statusLines(sl)) {
                    sp.sendSystemMessage(line);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
