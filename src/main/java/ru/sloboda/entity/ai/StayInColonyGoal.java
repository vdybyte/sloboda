package ru.sloboda.entity.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import ru.sloboda.colony.Colony;
import ru.sloboda.entity.SettlerEntity;

/** Не даёт гуляющему жителю уйти далеко от Вечевого камня. */
public class StayInColonyGoal extends Goal {
    private static final double LEAVE_DIST = 40.0;
    private static final double BACK_DIST = 12.0;

    private final SettlerEntity settler;
    private BlockPos home;

    public StayInColonyGoal(SettlerEntity settler) {
        this.settler = settler;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Colony colony = settler.getColony();
        if (colony == null) return false;
        home = colony.center();
        return settler.distanceToSqr(Vec3.atBottomCenterOf(home)) > LEAVE_DIST * LEAVE_DIST;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && !settler.getNavigation().isDone()
                && settler.distanceToSqr(Vec3.atBottomCenterOf(home)) > BACK_DIST * BACK_DIST;
    }

    @Override
    public void start() {
        settler.getNavigation().moveTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, SettlerEntity.WALK_SPEED);
    }
}
