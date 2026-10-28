package ru.sloboda.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;

/**
 * Амбар — окно в общий склад слободы. Все амбары одной слободы показывают
 * одно и то же содержимое, поэтому сломанный амбар ничего не теряет.
 */
public class GranaryBlock extends Block {
    public GranaryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl)) return;
        Colony colony = ColonyData.get(sl).at(pos);
        if (colony != null) {
            colony.addGranary(pos);
            ColonyData.markDirty(sl);
            if (placer instanceof ServerPlayer p) {
                p.sendSystemMessage(Component.literal("Амбар подключён к слободе «" + colony.name() + "».")
                        .withStyle(ChatFormatting.GREEN));
            }
        } else if (placer instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("Амбар работает только в пределах слободы ("
                    + Colony.RADIUS + " блоков от Вечевого камня).").withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Colony colony = ColonyData.get(sl).at(pos);
            if (colony == null) {
                sp.sendSystemMessage(Component.literal("Амбар вне слободы — склада нет.").withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            colony.addGranary(pos);
            sp.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> ChestMenu.sixRows(id, inventory, colony.storage()),
                    Component.literal("Амбар слободы «" + colony.name() + "»")));
        }
        return InteractionResult.SUCCESS;
    }
}
