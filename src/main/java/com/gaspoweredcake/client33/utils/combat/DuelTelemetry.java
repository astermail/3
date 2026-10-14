package com.gaspoweredcake.client33.utils.combat;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.entity.player.AttackEntityEvent;
import com.gaspoweredcake.client33.events.entity.player.FinishUsingItemEvent;
import com.gaspoweredcake.client33.events.game.GameJoinedEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.utils.PostInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import static com.gaspoweredcake.client33.Client33.mc;

/** Counts observed server melee damage, drinks, and visible potion throws; no inferred server hits. */
public final class DuelTelemetry {
    public record Stats(String name, int hits, int potions) {}
    private record Pending(ClientboundDamageEventPacket packet, Object world) {}
    private static final DuelTelemetry INSTANCE = new DuelTelemetry();
    private final Map<UUID, Stats> stats = new LinkedHashMap<>();
    private final Map<UUID, Integer> drinking = new HashMap<>();
    private final Set<UUID> projectiles = new HashSet<>();
    private final ArrayBlockingQueue<Pending> damage = new ArrayBlockingQueue<>(256);
    private Object world;
    private UUID target;
    private long attacked;
    private double reach;
    private DuelTelemetry() {}
    @PostInit public static void init() { Client33.EVENT_BUS.subscribe(INSTANCE); }
    public static Stats stats(Player player) { return INSTANCE.stats.getOrDefault(player.getUUID(), new Stats(player.getName().getString(), 0, 0)); }
    public static Player target() {
        if (mc.level == null || INSTANCE.world != mc.level || CombatTelemetry.now() - INSTANCE.attacked > 5000) return null;
        return mc.level.getPlayerByUUID(INSTANCE.target);
    }
    public static Player opponent() {
        if (mc.level == null || INSTANCE.world != mc.level || INSTANCE.target == null) return null;
        return mc.level.getPlayerByUUID(INSTANCE.target);
    }
    public static double reach() { return INSTANCE.reach; }
    public static boolean hasAttack() { return INSTANCE.target != null; }
    public static void reset() { INSTANCE.clear(); }
    private void clear() { stats.clear(); drinking.clear(); projectiles.clear(); damage.clear(); world = mc.level; target = null; reach = 0; }
    @EventHandler private void join(GameJoinedEvent event) { clear(); }
    @EventHandler private void leave(GameLeftEvent event) { clear(); }
    @EventHandler(priority = -2000) private void attack(AttackEntityEvent event) {
        if (event.isCancelled() || mc.player == null || !(event.entity instanceof Player player)) return;
        if (world != mc.level) clear();
        target = player.getUUID(); attacked = CombatTelemetry.now();
        reach = Math.sqrt(player.getBoundingBox().distanceToSqr(mc.player.getEyePosition()));
    }
    private void add(Player player, boolean hit) {
        Stats old = stats(player);
        stats.put(player.getUUID(), new Stats(player.getName().getString(), old.hits + (hit ? 1 : 0), old.potions + (hit ? 0 : 1)));
        while (stats.size() > 128) stats.remove(stats.keySet().iterator().next());
    }
    @EventHandler(priority = -2000) private void packet(PacketEvent.Receive event) {
        if (!event.isCancelled() && event.packet instanceof ClientboundDamageEventPacket packet) damage.offer(new Pending(packet, mc.level));
    }
    @EventHandler private void drank(FinishUsingItemEvent event) {
        if (mc.player != null && event.itemStack.is(Items.POTION)) add(mc.player, false);
    }
    @EventHandler private void tick(TickEvent.Post event) {
        if (world != mc.level) clear();
        if (mc.level == null || mc.player == null) return;
        for (int i = 0; i < 256; i++) {
            var pending = damage.poll(); if (pending == null) break;
            var packet = pending.packet;
            if (pending.world == mc.level && packet.sourceType().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && mc.level.getEntity(packet.entityId()) instanceof Player && packet.getSource(mc.level).getEntity() instanceof Player source) add(source, true);
        }
        Set<UUID> present = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownSplashPotion potion) {
                present.add(potion.getUUID());
                if (projectiles.add(potion.getUUID()) && potion.getOwner() instanceof Player player) add(player, false);
            } else if (entity instanceof ThrownLingeringPotion potion) {
                present.add(potion.getUUID());
                if (projectiles.add(potion.getUUID()) && potion.getOwner() instanceof Player player) add(player, false);
            }
        }
        projectiles.retainAll(present);
        Set<UUID> players = new HashSet<>();
        for (Player player : mc.level.players()) {
            players.add(player.getUUID());
            if (player == mc.player) continue;
            boolean now = player.isUsingItem() && player.getUseItem().is(Items.POTION);
            int before = drinking.getOrDefault(player.getUUID(), 0);
            if (before >= 30 && !now && player.isAlive()
                && (player.getMainHandItem().is(Items.GLASS_BOTTLE) || player.getOffhandItem().is(Items.GLASS_BOTTLE))) add(player, false);
            drinking.put(player.getUUID(), now ? before + 1 : 0);
        }
        drinking.keySet().retainAll(players);
    }
}
