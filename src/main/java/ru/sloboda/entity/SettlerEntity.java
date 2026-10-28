package ru.sloboda.entity;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.sloboda.colony.Colony;
import ru.sloboda.colony.ColonyData;
import ru.sloboda.entity.ai.BuilderJob;
import ru.sloboda.entity.ai.CarrierJob;
import ru.sloboda.entity.ai.Job;
import ru.sloboda.entity.ai.SettlerWorkGoal;
import ru.sloboda.entity.ai.StayInColonyGoal;
import ru.sloboda.entity.ai.WoodcutterJob;

public class SettlerEntity extends PathfinderMob {
    public static final double WALK_SPEED = 0.6;

    private final SimpleContainer inventory = new SimpleContainer(9);
    private Profession profession = Profession.NONE;
    private String givenName = "Поселенец";
    private UUID colonyId;
    private Job job;
    private int healTimer;

    public SettlerEntity(EntityType<? extends SettlerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 0.6, 0.8));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new SettlerWorkGoal(this));
        this.goalSelector.addGoal(4, new StayInColonyGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ колония и ремесло

    public void joinColony(UUID colony, Profession profession, String name) {
        this.colonyId = colony;
        this.givenName = name;
        setProfession(profession);
    }

    public void setProfession(Profession profession) {
        this.profession = profession;
        this.job = null;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(profession.tool()));
        refreshName();
    }

    public Profession getProfession() {
        return profession;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public Colony getColony() {
        if (colonyId == null || !(this.level() instanceof ServerLevel sl)) return null;
        return ColonyData.get(sl).byId(colonyId);
    }

    public Job getJob() {
        if (job == null) {
            job = switch (profession) {
                case WOODCUTTER -> new WoodcutterJob(this);
                case BUILDER -> new BuilderJob(this);
                case CARRIER -> new CarrierJob(this);
                case NONE -> null;
            };
        }
        return job;
    }

    private void refreshName() {
        this.setCustomName(Component.literal(givenName + " · " + profession.title()));
        this.setCustomNameVisible(true);
    }

    public void dropInventory() {
        Containers.dropContents(this.level(), this.blockPosition(), inventory);
    }

    // ------------------------------------------------------------------ жизнь

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel && ++healTimer >= 200) {
            healTimer = 0;
            if (this.getHealth() < this.getMaxHealth()) this.heal(1.0F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel sl) {
            dropInventory();
            Colony colony = getColony();
            if (colony != null) {
                colony.onSettlerDied(this, sl);
                ColonyData.markDirty(sl);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                setProfession(profession.next());
                sp.sendSystemMessage(Component.literal(givenName + " теперь " + profession.title().toLowerCase())
                        .withStyle(ChatFormatting.GREEN));
            } else {
                Job j = getJob();
                String doing = j == null ? "отдыхает" : j.status();
                int items = 0;
                for (int s = 0; s < inventory.getContainerSize(); s++) items += inventory.getItem(s).getCount();
                Colony colony = getColony();
                sp.sendSystemMessage(Component.literal(givenName + " — " + profession.title()
                        + (colony != null ? " из слободы «" + colony.name() + "»" : " (без слободы)"))
                        .withStyle(ChatFormatting.GOLD));
                sp.sendSystemMessage(Component.literal("Сейчас: " + doing + ". В котомке: " + items + " предм."));
                sp.sendSystemMessage(Component.literal("Shift + ПКМ — сменить ремесло").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ сохранение

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Profession", profession.id());
        output.putString("GivenName", givenName);
        if (colonyId != null) {
            output.store("Colony", UUIDUtil.CODEC, colonyId);
        }
        inventory.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.profession = Profession.byId(input.getStringOr("Profession", "none"));
        this.givenName = input.getStringOr("GivenName", "Поселенец");
        this.colonyId = input.read("Colony", UUIDUtil.CODEC).orElse(null);
        this.inventory.clearContent();
        for (ItemStack stack : input.listOrEmpty("Inventory", ItemStack.CODEC)) {
            this.inventory.addItem(stack);
        }
        this.job = null;
    }
}
