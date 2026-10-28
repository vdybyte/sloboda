package ru.sloboda.entity.ai;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import ru.sloboda.colony.Colony;
import ru.sloboda.entity.SettlerEntity;

public class SettlerWorkGoal extends Goal {
    private final SettlerEntity settler;

    public SettlerWorkGoal(SettlerEntity settler) {
        this.settler = settler;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean hasWork() {
        if (!(settler.level() instanceof ServerLevel level)) return false;
        Colony colony = settler.getColony();
        Job job = settler.getJob();
        return colony != null && job != null && job.canWork(level, colony);
    }

    @Override
    public boolean canUse() {
        return hasWork();
    }

    @Override
    public boolean canContinueToUse() {
        return hasWork();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(settler.level() instanceof ServerLevel level)) return;
        Colony colony = settler.getColony();
        Job job = settler.getJob();
        if (colony != null && job != null) {
            job.tick(level, colony);
        }
    }

    @Override
    public void stop() {
        Job job = settler.getJob();
        if (job != null) job.stop();
    }
}
