/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.mixin.LocalPlayerAccessor;
import com.gaspoweredcake.client33.utils.player.SprintInput;
import com.gaspoweredcake.client33.settings.BoolSetting;
import com.gaspoweredcake.client33.settings.EnumSetting;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public class Sprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("sprint-mode")
        .description("Strict follows vanilla sprint eligibility. Rage also requests sprint sideways, backwards or while stationary when configured.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Boolean> keepSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-sprint")
        .description("Whether to keep sprinting after attacking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> unsprintOnHit = sgGeneral.add(new BoolSetting.Builder()
        .name("unsprint-on-hit")
        .description("Whether to stop sprinting before attacking, to ensure you get crits and sweep attacks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> unsprintInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("unsprint-in-water")
        .description("Whether to stop sprinting when in water.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    private final Setting<Boolean> permaSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-while-stationary")
        .description("Sprint even when not moving.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    public Sprint() {
        super(Categories.Movement, "sprint", "Automatically sprints.");
    }

    private boolean restoreAfterAttack;

    @Override public void onActivate() { restoreAfterAttack = false; }
    @Override public void onDeactivate() { restoreAfterAttack = false; }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundAttackPacket)) return;
        restoreAfterAttack = false;
        if (event.isCancelled() || !unsprintOnHit.get() || mc.player == null || mc.getConnection() == null || !mc.player.isSprinting()) return;
        restoreAfterAttack = true;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        mc.player.setSprinting(false);
    }

    @EventHandler
    private void onPacketSent(PacketEvent.Sent event) {
        if (!(event.packet instanceof ServerboundAttackPacket)) return;
        boolean restore = restoreAfterAttack;
        restoreAfterAttack = false;
        if (!restore || !keepSprint.get() || !shouldSprint() || mc.player.isSprinting() || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        mc.player.setSprinting(true);
    }

    public boolean allowsEnvironment() {
        if (!isActive() || mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        if (mc.gui.screen() != null) {
            GUIMove guiMove = Modules.get().get(GUIMove.class);
            if (!guiMove.isActive() || !guiMove.sprint.get() || guiMove.skip()) return false;
        }
        return true;
    }

    public boolean shouldSprint() {
        if (!allowsEnvironment()) return false;
        boolean rage = mode.get() == Mode.Rage;
        if (rage && unsprintInWater.get() && mc.player.isInWater()) return false;
        var movement = mc.player.input.getMoveVector();
        if (!SprintInput.moving(movement.y, movement.x, rage, rage && permaSprint.get())) return false;
        if (rage) return true;

        var access = (LocalPlayerAccessor) mc.player;
        return access.client33$isSprintingPossible(mc.player.getAbilities().flying)
            && !access.client33$isSlowDueToUsingItem()
            && (!mc.player.isFallFlying() || mc.player.isUnderWater())
            && (!mc.player.isMovingSlowly() || mc.player.isUnderWater())
            && (!mc.player.horizontalCollision || mc.player.minorHorizontalCollision);
    }

    public boolean rageSprint() {
        return isActive() && mode.get() == Mode.Rage;
    }

    public boolean unsprintInWater() {
        return isActive() && unsprintInWater.get();
    }

    public boolean stopSprinting() {
        return !isActive() || !keepSprint.get();
    }
}
