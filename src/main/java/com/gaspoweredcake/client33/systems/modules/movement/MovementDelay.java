package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.network.TimedPacketQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

/** Delays movement only. Actions flush older movement before they are sent. */
public abstract class MovementDelay extends Module {
    protected final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder().name("delay-ms").description("Movement delay in milliseconds. Server movement rules still apply.").defaultValue(100).range(0, 500).sliderRange(0, 500).build());
    private final TimedPacketQueue<ServerboundMovePlayerPacket> queue = new TimedPacketQueue<>(256);
    private Connection connection;
    private long resumeAt;
    protected MovementDelay(String name, String description, String alias) { super(Categories.Movement, name, description, alias); }
    protected abstract boolean shouldDelay();
    private boolean ownsConnection() { return mc.getConnection() != null && mc.getConnection().getConnection() == connection && connection.isConnected(); }
    @Override public synchronized void onActivate() { queue.clear(); connection = null; resumeAt = 0; }
    @Override public synchronized void onDeactivate() { flush(true); connection = null; }
    @EventHandler private synchronized void leave(GameLeftEvent event) { queue.clear(); connection = null; }
    @EventHandler private synchronized void received(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            // Drop movement computed before the authoritative correction.
            queue.clear(); resumeAt = CombatTelemetry.now() + 500;
        }
    }
    @EventHandler private synchronized void tick(TickEvent.Post event) { flush(!shouldDelay() || Modules.get().isActive(Blink.class)); }
    @EventHandler(priority = -1000) private synchronized void send(PacketEvent.Send event) {
        if (event.isCancelled() || mc.player == null || mc.getConnection() == null) return;
        if (connection != event.connection) { queue.clear(); connection = event.connection; }
        if (!(event.packet instanceof ServerboundMovePlayerPacket movement)) {
            if (!(event.packet instanceof ServerboundKeepAlivePacket) && !(event.packet instanceof ServerboundPongPacket)) flush(true);
            return;
        }
        if (!shouldDelay() || CombatTelemetry.now() < resumeAt || Modules.get().isActive(Blink.class)
            || otherDelayActive() || delay.get() == 0) { flush(true); return; }
        if (!queue.offer(movement, CombatTelemetry.now(), delay.get())) {
            flush(true);
            queue.offer(movement, CombatTelemetry.now(), delay.get());
        }
        event.cancel();
    }
    private boolean otherDelayActive() { return this instanceof BackTrack && Modules.get().isActive(FakeLag.class); }
    private void flush(boolean all) {
        if (connection == null || !ownsConnection()) { queue.clear(); return; }
        for (var packet : queue.drain(CombatTelemetry.now(), all)) connection.send(packet, null, true);
    }
    @Override public String getInfoString() { return queue.size() + " queued"; }
}
