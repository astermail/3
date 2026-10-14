/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.friends.Friends;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.player.AutoEat;
import com.gaspoweredcake.client33.systems.modules.player.AutoGap;
import com.gaspoweredcake.client33.utils.entity.EntityAgeTest;
import com.gaspoweredcake.client33.utils.entity.EntityUtils;
import com.gaspoweredcake.client33.utils.entity.SortPriority;
import com.gaspoweredcake.client33.utils.entity.Target;
import com.gaspoweredcake.client33.utils.entity.fakeplayer.FakePlayerManager;
import com.gaspoweredcake.client33.utils.misc.TickTimer;
import com.gaspoweredcake.client33.utils.entity.fakeplayer.FakePlayerEntity;
import com.gaspoweredcake.client33.utils.player.FindItemResult;
import com.gaspoweredcake.client33.utils.player.InvUtils;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.player.Rotations;
import com.gaspoweredcake.client33.utils.world.TickRate;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.combat.AttackCadence;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> serverCooldown = sgTiming.add(new BoolSetting.Builder()
        .name("server-cooldown").description("Estimate cooldown from server time packets, sent attacks and weapon speed. Falls back to vanilla timing when server time samples are unavailable or stale.")
        .defaultValue(true).build());
    private final Setting<Integer> cooldownMargin = sgTiming.add(new IntSetting.Builder()
        .name("cooldown-margin-ms").description("Extra timing margin after weapon recharge. Custom Delay can only add more waiting.")
        .defaultValue(25).range(0, 250).sliderRange(0, 250).build());

    // General

    private final Setting<AttackItems> attackWhenHolding = sgGeneral.add(new EnumSetting.Builder<AttackItems>()
        .name("attack-when-holding")
        .description("Only attacks an entity when a specified item is in your hand.")
        .defaultValue(AttackItems.Weapons)
        .build()
    );

    private final Setting<List<Item>> weapons = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-weapon-types")
        .description("Which types of weapons to attack with (if you select the diamond sword, any type of sword may be used to attack).")
        .defaultValue(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT)
        .filter(FILTER::contains)
        .visible(() -> attackWhenHolding.get() == AttackItems.Weapons)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("Determines when you should rotate towards the target.")
        .defaultValue(RotationMode.Always)
        .build()
    );

    private final Setting<Target> aimAt = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("aim-at")
        .description("Which part of the target to aim at.")
        .defaultValue(Target.Body)
        .visible(() -> rotation.get() != RotationMode.None)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to an acceptable weapon when attacking the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches to your previous slot when done attacking the target.")
        .defaultValue(false)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("shield-mode")
        .description("""
                What to do when your target is blocking with a shield:
                - Ignore:   Don't attack them if they are blocking
                - Break:    Swap to an axe to disable the shield (Only if Auto Switch is enabled)
                - None:     Attack them as normal
            """)
        .defaultValue(ShieldMode.None)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks when holding left click.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-look")
        .description("Only attacks when looking at an entity.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Freezes Baritone temporarily until you are finished attacking the entity.")
        .defaultValue(true)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("Maximum candidates to consider. Attacks one ready target per weapon recharge.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(() -> !onlyOnLook.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to attack it.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range the entity can be attacked through walls.")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<EntityAgeTest> passiveMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAgeTest>()
        .name("passive-mob-age-filter")
        .description("Determines the age of passive mobs to target (animals, villagers).")
        .defaultValue(EntityAgeTest.Adult)
        .build()
    );

    private final Setting<EntityAgeTest> hostileMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAgeTest>()
        .name("hostile-mob-age-filter")
        .description("Determines the age of hostile mobs to target (zombies, piglins, hoglins, zoglins).")
        .defaultValue(EntityAgeTest.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to attack mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Will only attack sometimes passive mobs if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Will avoid attacking mobs you tamed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepTarget = sgTargeting.add(new BoolSetting.Builder()
        .name("keep-target")
        .description("Keeps the primary target until it leaves range or becomes invalid.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fieldOfView = sgTargeting.add(new DoubleSetting.Builder()
        .name("field-of-view")
        .description("Horizontal targeting cone in degrees. 360 targets all directions.")
        .defaultValue(360)
        .range(1, 360)
        .sliderRange(30, 360)
        .build()
    );

    // Timing

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Does not attack while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-CA")
        .description("Does not attack while CA is placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS-sync")
        .description("Scales custom attack delays to the server tick rate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Add a minimum interval between charged attacks. Never bypasses weapon recharge.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("Minimum ticks between attacks, in addition to full weapon recharge.")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("How many ticks to wait before hitting an entity after switching hotbar slots.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final static Set<Item> FILTER = Set.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_SPEAR, Items.TRIDENT);
    private final List<Entity> targets = new ArrayList<>();
    private final TickTimer switchTimer = new TickTimer();
    private final TickTimer attackTimer = new TickTimer();
    private final AttackCadence cadence = new AttackCadence();
    private final Setting<Boolean> waitHurt = sgTiming.add(new BoolSetting.Builder()
        .name("wait-for-hurt-recovery")
        .description("Avoid attacking a living target still in its observed hurt animation; prefer another ready target.")
        .defaultValue(true).build());
    private long attackGeneration;
    private String status = "Ready";
    private boolean wasPathing = false;
    public boolean attacking, swapped;
    public static int previousSlot;

    public KillAura() {
        super(Categories.Combat, "kill-aura", "Attacks specified entities around you.");
    }

    @Override
    public void onActivate() {
        targets.clear();
        attacking = false;
        wasPathing = false;
        previousSlot = -1;
        swapped = false;
        switchTimer.reset();
        attackTimer.reset();
        cadence.reset();
        attackGeneration++;
        status = "Ready";
    }

    @Override
    public void onDeactivate() {
        stopAttacking();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        attackGeneration++;
        switchTimer.advance();
        attackTimer.advance();
        if (mc.player == null || mc.level == null || mc.gameMode == null
            || !mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) {
            pause("Player unavailable");
            return;
        }
        if (mc.gui.screen() != null) { pause("Screen open"); return; }
        if (Modules.get().get(AutoEat.class).pausesKillAura() || Modules.get().get(AutoGap.class).pausesKillAura()) {
            pause("Eating");
            return;
        }
        if (com.gaspoweredcake.client33.utils.player.PotionUse.anyBusy()) {
            pause("Healing or throwing potion");
            return;
        }
        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) {
            pause("Using item or mining");
            return;
        }
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) {
            pause("Waiting for click");
            return;
        }
        if (CombatTelemetry.networkQuietMillis() >= 3000 && pauseOnLag.get()) {
            pause("No server packets");
            return;
        }
        if (pauseOnCA.get() && Modules.get().get(CrystalAura.class).isActive() && Modules.get().get(CrystalAura.class).kaTimer > 0) {
            pause("Crystal Aura placing");
            return;
        }
        if (onlyOnLook.get()) {
            Entity targeted = mc.crosshairPickEntity;

            if (targeted == null || !entityCheck(targeted)) {
                pause("No target under crosshair");
                return;
            }

            targets.clear();
            targets.add(mc.crosshairPickEntity);
        } else {
            collectTargets();
        }

        if (targets.isEmpty()) {
            pause("No valid targets");
            return;
        }

        Entity primary = targets.getFirst();

        if (autoSwitch.get()) {
            int selectedSlot = mc.player.getInventory().getSelectedSlot();
            ItemStack heldItem = mc.player.getMainHandItem();
            FindItemResult weaponResult = new FindItemResult(selectedSlot, heldItem.getCount());

            if (!usableForTarget(heldItem, primary)) {
                weaponResult = InvUtils.find(itemStack -> usableForTarget(itemStack, primary), 0, 8);
            }

            if (!weaponResult.found()) {
                pause("No weapon in hotbar");
                return;
            }

            if (weaponResult.slot() != selectedSlot) {
                if (!swapped) {
                    previousSlot = selectedSlot;
                    swapped = true;
                }
                InvUtils.swap(weaponResult.slot(), false);
            }
        }

        if (!usableForTarget(mc.player.getMainHandItem(), primary)) {
            pause("Hold a configured weapon within reach");
            return;
        }

        attacking = true;
        status = !targetRecovered(primary) ? "Waiting for target recovery" : delayCheck() ? "Ready to attack" : "Charging weapon";
        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }

        RotationMode rotationMode = rotation.get();
        if (delayCheck() && targetRecovered(primary)) {
            long generation = attackGeneration;
            Object player = mc.player, world = mc.level;
            Runnable hit = () -> {
                if (generation == attackGeneration && isActive() && attacking
                    && mc.player == player && mc.level == world && mc.gameMode != null
                    && mc.player.isAlive() && mc.gui.screen() == null
                    && primary.level() == mc.level && delayCheck() && targetRecovered(primary)
                    && (!pauseOnUse.get() || (!mc.player.isUsingItem() && !mc.gameMode.isDestroying()))
                    && !com.gaspoweredcake.client33.utils.player.PotionUse.anyBusy()
                    && !Modules.get().get(AutoEat.class).pausesKillAura() && !Modules.get().get(AutoGap.class).pausesKillAura()
                    && (!onlyOnClick.get() || mc.options.keyAttack.isDown())
                    && (!onlyOnLook.get() || mc.crosshairPickEntity == primary)
                    && usableForTarget(mc.player.getMainHandItem(), primary) && entityCheck(primary)) attack(primary);
            };
            // Queue one attack per charge; extra candidates are alternatives, never a same-tick burst.
            if (rotationMode == RotationMode.None) hit.run();
            else rotateTo(primary, hit);
        } else if (rotationMode == RotationMode.Always) {
            rotateTo(primary, null);
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) {
            switchTimer.waitTicks(switchDelay.get());
        }
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Sent event) {
        if (event.packet instanceof ServerboundAttackPacket) cadence.record(CombatTelemetry.now());
    }

    public void pauseForEating() {
        pause("Eating");
    }

    private void pause(String reason) {
        stopAttacking();
        status = reason;
    }

    private void stopAttacking() {
        attackGeneration++;
        targets.clear();
        attacking = false;
        if (wasPathing) {
            PathManagers.get().resume();
            wasPathing = false;
        }
        if (swapBack.get() && swapped && previousSlot >= 0 && mc.player != null) {
            InvUtils.swap(previousSlot, false);
        }
        swapped = false;
        previousSlot = -1;
    }

    private boolean shouldShieldBreak(Entity target) {
        if (!autoSwitch.get() || shieldMode.get() != ShieldMode.Break) return false;
        return target instanceof Player player && Modules.get().get(ShieldPredict.class).blocks(player);
    }

    private boolean usableForTarget(ItemStack stack, Entity target) {
        return (shouldShieldBreak(target) ? stack.getItem() instanceof AxeItem : acceptableWeapon(stack))
            && mc.player.isWithinAttackRange(stack, target.getBoundingBox(), 0);
    }

    private boolean hasReachableWeapon(Entity target) {
        if (usableForTarget(mc.player.getMainHandItem(), target)) return true;
        if (!autoSwitch.get()) return false;
        for (int slot = 0; slot < 9; slot++) {
            if (usableForTarget(mc.player.getInventory().getItem(slot), target)) return true;
        }
        return false;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive())
            return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (entity instanceof Player player && player.isSpectator()) return false;

        Vec3 eye = mc.player.getEyePosition();
        AABB hitbox = entity.getBoundingBox();
        double dx = eye.x - Mth.clamp(eye.x, hitbox.minX, hitbox.maxX);
        double dy = eye.y - Mth.clamp(eye.y, hitbox.minY, hitbox.maxY);
        double dz = eye.z - Mth.clamp(eye.z, hitbox.minZ, hitbox.maxZ);
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > range.get() * range.get()) return false;
        if (!hasReachableWeapon(entity)) return false;
        double wallReach = Math.min(range.get(), wallsRange.get());
        if (!PlayerUtils.canSeeEntity(entity) && distanceSquared > wallReach * wallReach) return false;
        if (Math.abs(Mth.wrapDegrees(Rotations.getYaw(entity) - mc.player.getYRot())) > fieldOfView.get() / 2) return false;
        if (ignoreTamed.get()) {
            if (entity instanceof OwnableEntity tameable
                && tameable.getOwner() != null
                && tameable.getOwner().equals(mc.player)
            ) return false;
        }
        if (ignorePassive.get()) {
            if (entity instanceof EnderMan enderman && !enderman.isCreepy()) return false;
            if ((entity instanceof Piglin || entity instanceof ZombifiedPiglin || entity instanceof Wolf) && !((Mob) entity).isAggressive())
                return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (shieldMode.get() == ShieldMode.Ignore && Modules.get().get(ShieldPredict.class).blocks(player)) return false;
            if (player instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) return false;
        }
        if (entity instanceof LivingEntity livingEntity) {
            // Hostile mobs with baby variants (zombies, piglins, hoglins, zoglins)
            if (entity instanceof Zombie || entity instanceof Piglin
                || entity instanceof Hoglin || entity instanceof Zoglin) {
                return hostileMobAgeFilter.get().test(livingEntity);
            }
            // Passive mobs with baby variants (animals, villagers)
            if (entity instanceof AgeableMob && (!(entity instanceof Frog || entity instanceof Parrot))) {
                return passiveMobAgeFilter.get().test(livingEntity);
            }
        }
        return true;
    }

    private void collectTargets() {
        Entity previous = getTarget();
        targets.clear();
        // The world's spatial query avoids sorting every rendered entity each tick.
        targets.addAll(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range.get()), this::entityCheck));
        FakePlayerManager.forEach(player -> {
            if (!targets.contains(player) && entityCheck(player)) targets.add(player);
        });
        targets.sort((left, right) -> {
            int recovery = Boolean.compare(!targetRecovered(left), !targetRecovered(right));
            if (recovery != 0) return recovery;
            if (priority.get() != SortPriority.ClosestAngle) return priority.get().compare(left, right);
            return Double.compare(angleScore(left), angleScore(right));
        });
        if (keepTarget.get() && previous != null && targets.contains(previous)
            && (targetRecovered(previous) || targets.stream().noneMatch(this::targetRecovered))) {
            targets.remove(previous); targets.addFirst(previous);
        }
        if (targets.size() > maxTargets.get()) targets.subList(maxTargets.get(), targets.size()).clear();
    }

    private double angleScore(Entity entity) {
        double yaw = Mth.wrapDegrees(Rotations.getYaw(entity) - mc.player.getYRot());
        double pitch = Rotations.getPitch(entity) - mc.player.getXRot();
        return yaw * yaw + pitch * pitch;
    }

    private boolean targetRecovered(Entity target) {
        return !waitHurt.get() || !(target instanceof LivingEntity living) || living.hurtTime <= 0;
    }

    private boolean delayCheck() {
        if (mc.player == null || !switchTimer.ready()) return false;
        long now = CombatTelemetry.now();
        double ticks = mc.player.getCurrentItemAttackStrengthDelay();
        double tickMillis = serverCooldown.get() && !CombatTelemetry.CLOCK.stale(now) ? CombatTelemetry.CLOCK.tickMillis() : 50;
        if (!cadence.ready(now, mc.player.getAttackStrengthScale(0), ticks, tickMillis, cooldownMargin.get())) return false;
        if (customDelay.get() && !attackTimer.ready()) return false;
        return !serverCooldown.get() || CombatTelemetry.CLOCK.readyWithFallback(now, ticks, cooldownMargin.get());
    }

    private void attack(Entity target) {
        // Reserve before invoking event hooks so reentrant/cancelled actions cannot cause a burst.
        cadence.record(CombatTelemetry.now());
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        status = "Charging weapon";
        attackTimer.waitTicks(TickTimer.serverDelay(hitDelay.get(), TickRate.INSTANCE.getTickRate(), tpsSync.get()));
    }

    private void rotateTo(Entity target, Runnable callback) {
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, aimAt.get()), callback);
    }

    private boolean acceptableWeapon(ItemStack stack) {
        if (attackWhenHolding.get() == AttackItems.All) return true;

        if (weapons.get().contains(Items.DIAMOND_SWORD) && stack.is(ItemTags.SWORDS)) return true;
        if (weapons.get().contains(Items.DIAMOND_AXE) && stack.is(ItemTags.AXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.is(ItemTags.PICKAXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.is(ItemTags.SHOVELS)) return true;
        if (weapons.get().contains(Items.DIAMOND_HOE) && stack.is(ItemTags.HOES)) return true;
        if (weapons.get().contains(Items.MACE) && stack.getItem() instanceof MaceItem) return true;
        if (weapons.get().contains(Items.DIAMOND_SPEAR) && stack.is(ItemTags.SPEARS)) return true;
        return weapons.get().contains(Items.TRIDENT) && stack.getItem() instanceof TridentItem;
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget()) + " / " + status;
        return status;
    }

    public enum AttackItems {
        Weapons,
        All
    }

    public enum RotationMode {
        Always,
        OnHit,
        None
    }

    public enum ShieldMode {
        Ignore,
        Break,
        None
    }

}
