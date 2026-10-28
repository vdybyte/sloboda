package ru.sloboda.entity.ai;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.sloboda.colony.Colony;
import ru.sloboda.entity.SettlerEntity;

/** Возчик: собирает всё, что валяется в слободе (саженцы, яблоки, щепки), и сносит в амбар. */
public class CarrierJob extends Job {
    private static final int SEARCH_RADIUS = 48;

    private enum State { FIND, FETCH, DELIVER }

    private State state = State.FIND;
    private ItemEntity target;

    public CarrierJob(SettlerEntity settler) {
        super(settler);
    }

    @Override
    public void tick(ServerLevel level, Colony colony) {
        switch (state) {
            case FIND -> {
                if (inventoryFull()) {
                    state = State.DELIVER;
                    return;
                }
                status = "высматривает, что подобрать";
                target = findItem(level, colony);
                if (target == null) {
                    if (carried() > 0) {
                        state = State.DELIVER;
                    } else {
                        idle(level, 100, "в слободе прибрано");
                    }
                    return;
                }
                state = State.FETCH;
            }
            case FETCH -> {
                status = "подбирает " + (target != null && target.isAlive()
                        ? target.getItem().getHoverName().getString() : "вещь");
                if (target == null || !target.isAlive()) {
                    state = State.FIND;
                    return;
                }
                if (moveNear(level, target.blockPosition(), 1.6)) {
                    ItemStack stack = target.getItem().copy();
                    int before = stack.getCount();
                    ItemStack rest = settler.getInventory().addItem(stack);
                    int picked = before - rest.getCount();
                    if (picked > 0) {
                        settler.take(target, picked);
                        swing();
                    }
                    if (rest.isEmpty()) {
                        target.discard();
                    } else {
                        target.setItem(rest);
                    }
                    target = null;
                    state = State.FIND;
                }
            }
            case DELIVER -> {
                if (deliver(level, colony)) {
                    state = State.FIND;
                }
            }
        }
    }

    private ItemEntity findItem(ServerLevel level, Colony colony) {
        AABB box = new AABB(colony.center()).inflate(SEARCH_RADIUS, 24, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.hasPickUpDelay() && colony.contains(e.blockPosition()));
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : items) {
            double d = settler.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
