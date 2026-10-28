package ru.sloboda.colony;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Изба 7×7: пол, бревенчатые углы, дощатые стены, дверь, окна-решётки,
 * ступенчатая крыша, верстак и лучина внутри.
 * План строится детерминированно, поэтому в сохранении хранится только номер шага.
 */
public final class HousePlan {
    public static final int S = HouseSite.SIZE;

    /** Шаг стройки. onlyIfEmpty — ставить, только если место пустое (подсыпка фундамента). */
    public record Step(BlockPos pos, BlockState state, boolean onlyIfEmpty, int halfPlanks) {}

    /** Сколько брёвен нужно на одну избу (одинаково для любой породы). */
    public static final int LOGS_NEEDED = computeLogs();

    private HousePlan() {}

    public static List<Step> build(BlockPos o, WoodSet w) {
        List<Step> steps = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1. Расчистка сверху вниз (чтобы песок/гравий не сыпался на голову).
        for (int y = 7; y >= 1; y--) {
            for (int x = 0; x < S; x++) {
                for (int z = 0; z < S; z++) {
                    steps.add(new Step(o.offset(x, y, z), air, false, 0));
                }
            }
        }

        // 2. Подсыпка под пол — бесплатная, земля.
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                steps.add(new Step(o.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), true, 0));
            }
        }

        // 3. Пол.
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                steps.add(new Step(o.offset(x, 0, z), w.planks().defaultBlockState(), false, 2));
            }
        }

        // 4. Стены, три венца.
        BlockState door = w.door().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        int mid = S / 2;
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < S; x++) {
                for (int z = 0; z < S; z++) {
                    boolean edgeX = x == 0 || x == S - 1;
                    boolean edgeZ = z == 0 || z == S - 1;
                    if (!edgeX && !edgeZ) {
                        continue;
                    }
                    BlockPos p = o.offset(x, y, z);
                    if (edgeX && edgeZ) {
                        steps.add(new Step(p, w.log().defaultBlockState(), false, 8));
                    } else if (x == mid && z == 0) {
                        if (y == 1) {
                            steps.add(new Step(p, door, false, 4)); // верхняя половина ставится вместе с нижней
                        } else if (y == 3) {
                            steps.add(new Step(p, w.planks().defaultBlockState(), false, 2));
                        }
                    } else if (y == 2 && ((edgeX && z == mid) || (x == mid && z == S - 1))) {
                        steps.add(new Step(p, w.fence().defaultBlockState(), false, 3));
                    } else {
                        steps.add(new Step(p, w.planks().defaultBlockState(), false, 2));
                    }
                }
            }
        }

        // 5. Потолок и ступенчатая крыша.
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                steps.add(new Step(o.offset(x, 4, z), w.planks().defaultBlockState(), false, 2));
            }
        }
        for (int x = 1; x < S - 1; x++) {
            for (int z = 1; z < S - 1; z++) {
                steps.add(new Step(o.offset(x, 5, z), w.planks().defaultBlockState(), false, 2));
            }
        }
        for (int x = 2; x < S - 2; x++) {
            for (int z = 2; z < S - 2; z++) {
                steps.add(new Step(o.offset(x, 6, z), w.slab().defaultBlockState(), false, 1));
            }
        }

        // 6. Обстановка.
        steps.add(new Step(o.offset(S - 2, 1, S - 2), Blocks.CRAFTING_TABLE.defaultBlockState(), false, 8));
        steps.add(new Step(o.offset(mid, 1, mid), Blocks.TORCH.defaultBlockState(), false, 0));

        return steps;
    }

    public static boolean isDoorLower(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static int computeLogs() {
        WoodSet oak = new WoodSet(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_DOOR);
        int half = 0;
        for (Step s : build(BlockPos.ZERO, oak)) {
            half += s.halfPlanks();
        }
        // 1 бревно = 4 доски = 8 полудосок
        return (half + 7) / 8;
    }

    /** Нужен для проверки «уже стоит то, что надо». */
    public static boolean sameBlock(BlockState a, BlockState b) {
        Block ba = a.getBlock();
        return ba == b.getBlock();
    }
}
