/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.entity.player.PlayerMoveEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.mixin.ServerboundMovePlayerPacketAccessor;
import com.gaspoweredcake.client33.mixininterface.IVec3;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Freecam;
import com.gaspoweredcake.client33.systems.modules.movement.elytrafly.ElytraFly;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import com.gaspoweredcake.client33.utils.movement.FlightMotion;
import com.gaspoweredcake.client33.utils.movement.FlightRecovery;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

public class Flight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRecovery = settings.createGroup("Server Recovery");
    private final SettingGroup sgAntiKick = settings.createGroup("Anti Kick");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Server only uses server-granted flight. Auto prefers granted flight, otherwise Velocity. Abilities changes local flags only.")
        .defaultValue(Mode.Auto)
        .onChanged(value -> resetControls())
        .build()
    );
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed").description("Ability flight speed, or one tenth of Velocity movement in blocks/tick.")
        .defaultValue(0.1).range(0, 1).sliderMax(0.3).build()
    );
    private final Setting<Boolean> verticalSpeedMatch = sgGeneral.add(new BoolSetting.Builder()
        .name("vertical-speed-match").description("Matches vertical movement speed to horizontal speed; otherwise uses half speed.")
        .defaultValue(false).build()
    );
    private final Setting<InputMode> inputMode = sgGeneral.add(new EnumSetting.Builder<InputMode>()
        .name("input-mode").description("Player Input uses processed movement; Keybinds reads bound key states.")
        .defaultValue(InputMode.PlayerInput).visible(() -> mode.get() == Mode.Auto || mode.get() == Mode.Velocity).build()
    );
    private final Setting<Double> acceleration = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration").description("Maximum horizontal and vertical speed change per tick in Velocity mode. Zero gives instant control.")
        .defaultValue(0.2).range(0, 2).sliderMax(1).visible(() -> mode.get() == Mode.Auto || mode.get() == Mode.Velocity).build()
    );
    private final Setting<Boolean> noSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("no-sneak").description("Suppresses the sneaking pose during Velocity flight; does not spoof ground support.")
        .defaultValue(false).visible(() -> mode.get() == Mode.Auto || mode.get() == Mode.Velocity).build()
    );
    private final Setting<Boolean> pauseOnCorrection = sgRecovery.add(new BoolSetting.Builder()
        .name("pause-on-correction").description("Accept server corrections and pause controls instead of repeatedly fighting setbacks.")
        .defaultValue(true).build()
    );
    private final Setting<Integer> correctionPause = sgRecovery.add(new IntSetting.Builder()
        .name("correction-pause-ms").description("Pause after a server position correction. Teleports also cause corrections.")
        .defaultValue(2000).range(250, 10000).sliderRange(250, 5000).visible(pauseOnCorrection::get).build()
    );
    private final Setting<Integer> correctionLimit = sgRecovery.add(new IntSetting.Builder()
        .name("correction-limit").description("Corrections within ten seconds before holding controls until you resume or re-enable Flight.")
        .defaultValue(3).range(1, 10).visible(pauseOnCorrection::get).build()
    );
    private final Setting<Keybind> resume = sgRecovery.add(new KeybindSetting.Builder()
        .name("resume-flight").description("Clear the correction hold. Re-enable Flight if this key is unbound.")
        .action(this::resumeControls).build()
    );
    private final Setting<AntiKickMode> antiKickMode = sgAntiKick.add(new EnumSetting.Builder<AntiKickMode>()
        .name("mode").description("Optional vanilla floating workaround for ungranted flight. Does not bypass server anti-flight checks.")
        .defaultValue(AntiKickMode.None).build()
    );
    private final Setting<Integer> delay = sgAntiKick.add(new IntSetting.Builder()
        .name("delay").description("Ticks between downward pulses.").defaultValue(20).range(1, 200).build()
    );
    private final Setting<Integer> offTime = sgAntiKick.add(new IntSetting.Builder()
        .name("off-time").description("Length of a downward pulse in ticks.").defaultValue(1).range(1, 20).build()
    );

    private record Permission(LocalPlayer player, ClientLevel world, boolean allowed, boolean flying, float speed) {}
    private final AtomicReference<Permission> permissions = new AtomicReference<>();
    private final FlightRecovery recovery = new FlightRecovery();
    private volatile LocalPlayer ownedPlayer;
    private volatile ClientLevel ownedWorld;
    private volatile Connection connection;
    private boolean savedAllowFlying, savedFlying;
    private float savedFlySpeed;
    private boolean abilitiesApplied;
    private boolean appliedAllowFlying, appliedFlying;
    private float appliedFlySpeed;
    private Vec3 startVelocity = Vec3.ZERO;
    private Vec3 controlledVelocity = Vec3.ZERO;
    private int pulseTicks;
    private double lastPacketY = Double.NaN;

    public Flight() {
        super(Categories.Movement, "flight", "Server-granted or controlled local flight, with smoothing and correction recovery. Server movement rules apply.");
    }

    @Override public void onActivate() { clearSession(); ensureSession(); }
    @Override public void onDeactivate() { restoreAbilities(); clearSession(); }
    @EventHandler private void leave(GameLeftEvent event) { restoreAbilities(); clearSession(); }

    private void clearSession() {
        ownedPlayer = null; ownedWorld = null; connection = null;
        permissions.set(null); recovery.reset(); abilitiesApplied = false;
        startVelocity = controlledVelocity = Vec3.ZERO; pulseTicks = 0; lastPacketY = Double.NaN;
    }

    private void ensureSession() {
        if (mc.player == null || mc.level == null) return;
        if (ownedPlayer == mc.player && ownedWorld == mc.level) return;
        // Never restore a previous world's abilities onto a new player.
        clearSession();
        savedAllowFlying = mc.player.getAbilities().mayfly;
        savedFlying = mc.player.getAbilities().flying;
        savedFlySpeed = mc.player.getAbilities().getFlyingSpeed();
        ownedPlayer = mc.player; ownedWorld = mc.level;
        connection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
    }

    private void resumeControls() {
        if (isActive()) { recovery.reset(); resetControls(); }
    }

    private void resetControls() {
        restoreAbilities(); startVelocity = controlledVelocity = Vec3.ZERO;
        pulseTicks = 0; lastPacketY = Double.NaN;
    }

    @EventHandler private void tick(TickEvent.Pre event) {
        ensureSession();
        if (ownedPlayer == null) return;
        var permission = permissions.getAndSet(null);
        if (permission != null && permission.player == ownedPlayer && permission.world == ownedWorld) {
            savedAllowFlying = permission.allowed; savedFlying = permission.flying; savedFlySpeed = permission.speed;
        }
        if (!controlsMovement()) { resetControls(); return; }
        pulseTicks = (pulseTicks + 1) % (delay.get() + offTime.get());
        if (usesAbilities()) {
            applyAbilities();
            if (antiKickMode.get() == AntiKickMode.Normal && downwardPulse() && !savedAllowFlying) mc.player.getAbilities().flying = false;
        } else {
            restoreAbilities();
            startVelocity = controlledVelocity;
            controlledVelocity = velocity();
            mc.player.setDeltaMovement(controlledVelocity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) private void move(PlayerMoveEvent event) {
        if (event.type != MoverType.SELF || !controlsMovement() || usesAbilities()) return;
        // Re-read processed input after vanilla input polling, with one acceleration step per tick.
        controlledVelocity = velocity();
        ((IVec3) event.movement).client33$set(controlledVelocity.x, controlledVelocity.y, controlledVelocity.z);
        mc.player.setDeltaMovement(controlledVelocity);
    }

    private Vec3 velocity() {
        if (mc.gui.screen() != null) return Vec3.ZERO;
        boolean bound = inputMode.get() == InputMode.Keybinds;
        var keys = mc.player.input.keyPresses;
        int forward = ((bound ? mc.options.keyUp.isDown() : keys.forward()) ? 1 : 0) - ((bound ? mc.options.keyDown.isDown() : keys.backward()) ? 1 : 0);
        int strafe = ((bound ? mc.options.keyRight.isDown() : keys.right()) ? 1 : 0) - ((bound ? mc.options.keyLeft.isDown() : keys.left()) ? 1 : 0);
        int vertical = ((bound ? mc.options.keyJump.isDown() : keys.jump()) ? 1 : 0) - ((bound ? mc.options.keyShift.isDown() : keys.shift()) ? 1 : 0);
        double horizontal = speed.get() * (mc.player.isSprinting() ? 15 : 10);
        double up = speed.get() * (verticalSpeedMatch.get() ? 10 : 5);
        var target = FlightMotion.target(mc.player.getYRot(), forward, strafe, vertical, horizontal, up);
        var smoothed = FlightMotion.approach(startVelocity, target, acceleration.get());
        if (antiKickMode.get() == AntiKickMode.Normal && downwardPulse() && !savedAllowFlying && !mc.player.onGround())
            return new Vec3(smoothed.x, Math.min(smoothed.y, -0.04), smoothed.z);
        return smoothed;
    }

    /** Also gates fluid/sneak mixins, so a paused Flight does not alter ordinary movement. */
    public boolean controlsMovement() {
        return isActive() && ownedPlayer != null && ownedPlayer == mc.player && ownedWorld == mc.level
            && mc.player.isAlive() && !mc.player.isPassenger() && !mc.player.isSpectator() && !mc.player.isFallFlying()
            && !Modules.get().isActive(Freecam.class) && !Modules.get().isActive(ElytraFly.class)
            && (mode.get() != Mode.Server || savedAllowFlying)
            && (!pauseOnCorrection.get() || recovery.allows(CombatTelemetry.now()));
    }

    private boolean usesAbilities() {
        return mode.get() == Mode.Abilities || mode.get() == Mode.Server || (mode.get() == Mode.Auto && savedAllowFlying);
    }
    private boolean downwardPulse() { return pulseTicks >= delay.get(); }

    private void applyAbilities() {
        var abilities = mc.player.getAbilities();
        // Server mode never manufactures permission; Abilities is explicitly a local-only mode.
        appliedAllowFlying = usesAbilities() && (savedAllowFlying || mode.get() == Mode.Abilities);
        appliedFlying = appliedAllowFlying;
        appliedFlySpeed = mode.get() == Mode.Server ? savedFlySpeed : speed.get().floatValue();
        boolean flightChanged = abilities.flying != appliedFlying;
        abilities.mayfly = appliedAllowFlying; abilities.flying = appliedFlying; abilities.setFlyingSpeed(appliedFlySpeed);
        abilitiesApplied = true;
        if (savedAllowFlying && flightChanged) mc.player.onUpdateAbilities();
    }
    private void restoreAbilities() {
        if (!abilitiesApplied) return;
        if (ownedPlayer != null && ownedPlayer == mc.player && ownedWorld == mc.level) {
            var abilities = ownedPlayer.getAbilities();
            boolean wasFlying = abilities.flying;
            if (abilities.mayfly == appliedAllowFlying) abilities.mayfly = savedAllowFlying;
            if (abilities.flying == appliedFlying) abilities.flying = savedFlying;
            if (abilities.getFlyingSpeed() == appliedFlySpeed) abilities.setFlyingSpeed(savedFlySpeed);
            if (savedAllowFlying && wasFlying != abilities.flying && mc.getConnection() != null
                && mc.getConnection().getConnection() == connection) ownedPlayer.onUpdateAbilities();
        }
        abilitiesApplied = false;
    }

    @EventHandler(priority = EventPriority.LOWEST) private void receive(PacketEvent.Receive event) {
        if (event.isCancelled() || event.connection != connection || ownedPlayer == null) return;
        if (event.packet instanceof ClientboundPlayerAbilitiesPacket packet) {
            // Do not cancel permissions. Vanilla applies them on the client thread.
            permissions.set(new Permission(ownedPlayer, ownedWorld, packet.canFly(), packet.isFlying(), packet.getFlyingSpeed()));
        } else if (event.packet instanceof ClientboundPlayerPositionPacket && pauseOnCorrection.get()) {
            recovery.corrected(CombatTelemetry.now(), correctionPause.get(), correctionLimit.get());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) private void send(PacketEvent.Send event) {
        if (event.isCancelled() || !controlsMovement() || savedAllowFlying || antiKickMode.get() != AntiKickMode.Packet
            || !(event.packet instanceof ServerboundMovePlayerPacket packet)) return;
        double currentY = packet.getY(mc.player.getY());
        boolean pulse = downwardPulse() && !mc.player.onGround() && !Double.isNaN(lastPacketY) && currentY >= lastPacketY - 0.03130;
        double y = pulse ? lastPacketY - 0.03130 : currentY;
        if (pulse) {
            if (packet.hasPosition()) ((ServerboundMovePlayerPacketAccessor) packet).client33$setY(y);
            else {
                event.cancel();
                // Silent send prevents recursive event dispatch; only the downward pulse needs a full position.
                if (packet.hasRotation()) event.sendSilently(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), y, mc.player.getZ(), packet.getYRot(0), packet.getXRot(0), packet.isOnGround(), mc.player.horizontalCollision));
                else event.sendSilently(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), y, mc.player.getZ(), packet.isOnGround(), mc.player.horizontalCollision));
            }
        }
        lastPacketY = y;
    }

    public float getFlyingSpeed() { return -1; }
    public boolean noSneak() { return controlsMovement() && !usesAbilities() && noSneak.get(); }
    @Override public String getInfoString() {
        if (mc.player == null) return "Waiting for world";
        if (mode.get() == Mode.Server && !savedAllowFlying) return "Server flight not granted";
        if (pauseOnCorrection.get() && !recovery.allows(CombatTelemetry.now())) return recovery.held() ? "Corrections: resume required" : "Server correction: paused";
        if (!controlsMovement()) return "Controls paused";
        return usesAbilities() ? (savedAllowFlying ? "Server abilities" : "Local abilities") : "Velocity";
    }
    public enum Mode { Auto, Abilities, Velocity, Server }
    public enum InputMode { PlayerInput, Keybinds }
    public enum AntiKickMode { Normal, Packet, None }
}
