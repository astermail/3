package com.gaspoweredcake.client33.utils.combat;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.game.GameJoinedEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.utils.PostInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

import static com.gaspoweredcake.client33.Client33.mc;

public final class CombatTelemetry {
    public static final ServerTickEstimator CLOCK = new ServerTickEstimator();
    private static volatile long lastPacketReceipt = now();
    private static final CombatTelemetry INSTANCE = new CombatTelemetry();
    private CombatTelemetry() {}
    @PostInit public static void init() { Client33.EVENT_BUS.subscribe(INSTANCE); }
    public static long now() { return System.nanoTime() / 1_000_000; }
    public static long networkQuietMillis() { return Math.max(0, now() - lastPacketReceipt); }
    public static int ping() {
        if (mc.player == null || mc.getConnection() == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, Math.min(2000, info.getLatency()));
    }
    public static int acknowledgementTicks() {
        return Math.max(4, Math.min(80, (int) Math.ceil((ping() + 2 * CLOCK.tickMillis()) / 50) + 2));
    }
    @EventHandler private void onReceive(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        long receipt = now();
        lastPacketReceipt = receipt;
        if (event.packet instanceof ClientboundSetTimePacket packet) CLOCK.sample(packet.gameTime(), receipt);
    }
    @EventHandler private void onSent(PacketEvent.Sent event) {
        if (event.packet instanceof ServerboundAttackPacket) CLOCK.attack(now());
    }
    @EventHandler private void onJoin(GameJoinedEvent event) { CLOCK.reset(); lastPacketReceipt = now(); }
    @EventHandler private void onLeave(GameLeftEvent event) { CLOCK.reset(); lastPacketReceipt = now(); }
}
