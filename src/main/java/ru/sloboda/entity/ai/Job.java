package ru.sloboda.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.sloboda.colony.Colony;
import ru.sloboda.entity.SettlerEntity;

/** Ремесло жителя: небольшая машина состояний, которую крутит SettlerWorkGoal. */
public abstract class Job {
    private static final int STUCK_LIMIT = 600;

    protected final SettlerEntity settler;
    protected String status = "осматривается";
    protected long idleUntil;
    private int moveTicks;
    private int repathTimer;

    protected Job(SettlerEntity settler) {
        this.settler = settler;
    }

    /** Есть ли сейчас работа (если нет — житель просто гуляет). */
    public boolean canWork(ServerLevel level, Colony colony) {
        return level.getGameTime() >= idleUntil;
    }

    public abstract void tick(ServerLevel level, Colony colony);

    public void stop() {
        settler.getNavigation().stop();
    }

    public String status() {
        return status;
    }

    protected void idle(ServerLevel level, int ticks, String why) {
        status = why;
        idleUntil = level.getGameTime() + ticks;
    }

    // ------------------------------------------------------------------ передвижение

    /**
     * Идёт к цели. Возвращает true, когда подошёл на reach блоков.
     * Если долго не может дойти — «срезает путь» (телепорт рядом с целью), чтобы не застревать навсегда.
     */
    protected boolean moveNear(ServerLevel level, BlockPos target, double reach) {
        Vec3 c = Vec3.atBottomCenterOf(target);
        if (settler.distanceToSqr(c) <= reach * reach) {
            moveTicks = 0;
            settler.getNavigation().stop();
            return true;
        }
        if (--repathTimer <= 0 || settler.getNavigation().isDone()) {
            repathTimer = 20;
            settler.getNavigation().moveTo(c.x, c.y, c.z, SettlerEntity.WALK_SPEED);
        }
        if (++moveTicks > STUCK_LIMIT) {
            moveTicks = 0;
            int y = Colony.groundY(level, target.getX(), target.getZ()) + 1;
            settler.teleportTo(target.getX() + 0.5, y, target.getZ() + 1.5);
        }
        return false;
    }

    protected void lookAt(BlockPos pos) {
        settler.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** В 26.3 взмах рукой требует анимацию — берём её у предмета в руке (топор, лопата и т. п.). */
    protected void swing() {
        settler.swing(InteractionHand.MAIN_HAND, settler.getMainHandItem().getInteractAnimation());
    }

    // ------------------------------------------------------------------ котомка

    protected int carried() {
        SimpleContainer inv = settler.getInventory();
        int n = 0;
        for (int s = 0; s < inv.getContainerSize(); s++) n += inv.getItem(s).getCount();
        return n;
    }

    protected boolean inventoryFull() {
        SimpleContainer inv = settler.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.isEmpty() || st.getCount() < st.getMaxStackSize()) return false;
        }
        return true;
    }

    /** Несёт котомку в амбар. Возвращает true, когда разгрузился. */
    protected boolean deliver(ServerLevel level, Colony colony) {
        status = "несёт добро в амбар";
        BlockPos storage = colony.nearestStorage(level, settler.blockPosition());
        if (!moveNear(level, storage, 3.0)) return false;
        lookAt(storage);
        swing();
        SimpleContainer inv = settler.getInventory();
        boolean full = false;
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.isEmpty()) continue;
            ItemStack rest = colony.insert(st);
            inv.setItem(s, rest);
            if (!rest.isEmpty()) full = true;
        }
        if (full) {
            idle(level, 200, "амбар полон — нужен ещё Амбар или разгрузи его");
        }
        return true;
    }
}
