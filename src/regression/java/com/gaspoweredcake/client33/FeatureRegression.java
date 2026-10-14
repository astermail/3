package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.events.entity.player.DoAttackEvent;
import com.gaspoweredcake.client33.utils.combat.CombatTargets;
import com.gaspoweredcake.client33.utils.entity.simulator.ThrowAim;
import com.gaspoweredcake.client33.utils.network.TimedPacketQueue;
import com.gaspoweredcake.client33.utils.player.HotbarOwnership;
import com.gaspoweredcake.client33.utils.player.PotionUse;
import com.gaspoweredcake.client33.utils.render.RadarGeometry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import java.util.List;

/** Boundary checks for actual shared combat, inventory, queue and HUD helpers. */
public final class FeatureRegression {
    public static void run() throws InterruptedException {
        var queue = new TimedPacketQueue<String>(3);
        check(queue.offer("first", 0, 100), "First movement is queued");
        check(queue.offer("second", 1, 10), "A setting change can lower later delays");
        check(queue.offer("third", 2, 200), "Third movement is queued");
        check(!queue.offer("overflow", 3, 0), "Queue bounds are enforced");
        check(queue.drain(99, false).isEmpty(), "Later movement cannot pass an older delayed packet");
        check(queue.drain(100, false).equals(List.of("first", "second")), "Movement remains FIFO across decreasing delays");
        check(queue.drain(101, true).equals(List.of("third")), "An action barrier flushes older movement");
        queue.offer("old connection", 0, 500); queue.clear();
        check(queue.drain(1000, true).isEmpty(), "Disconnect/correction discards queued movement");
        queue.offer("immediate", 10, -1);
        check(queue.drain(10, false).equals(List.of("immediate")), "Negative delays cannot defer packets");
        var concurrent = new TimedPacketQueue<Integer>(256);
        var first = new Thread(() -> { for (int i = 0; i < 100; i++) concurrent.offer(i, 0, 10); });
        var second = new Thread(() -> { for (int i = 100; i < 200; i++) concurrent.offer(i, 0, 10); });
        first.start(); second.start(); first.join(); second.join();
        var values = concurrent.drain(10, false);
        check(values.size() == 200 && new java.util.HashSet<>(values).size() == 200, "Packet and client threads never lose or duplicate queued entries");

        Object player = new Object(), world = new Object();
        var ownership = new HotbarOwnership(player, world, 2, 7);
        check(ownership.mayStart(player, world, 2), "Deferred use starts only from its original slot");
        check(!ownership.mayStart(player, world, 3), "A manual slot change cancels a queued use");
        check(ownership.mayRestore(player, world, 7), "Owned use may restore the original slot");
        check(!ownership.mayRestore(player, world, 4), "Manual slot selection takes priority over restoration");
        check(!ownership.mayRestore(new Object(), world, 7) && !ownership.mayStart(player, new Object(), 2), "Disconnect and dimension/player changes revoke ownership");

        var attack = DoAttackEvent.get(); attack.cancel();
        check(!DoAttackEvent.get().isCancelled(), "HitSelect cancelling one click must not cancel every later attack");
        near(CombatTargets.step(179, -179, 1), 180, "Aim crosses the shortest yaw wrap");
        near(CombatTargets.step(-179, 179, 1), -180, "Yaw wrap works in either direction");
        near(CombatTargets.step(20, 22, 5), 22, "Smoothing does not overshoot its target");
        var forward = RadarGeometry.project(0, 10, 0, 20, 100);
        near(forward.x(), 0, "Facing south centers a southern target"); near(forward.y(), -50, "Forward targets appear above the center");
        var east = RadarGeometry.project(10, 0, -90, 20, 100);
        near(east.x(), 0, "Radar follows yaw"); near(east.y(), -50, "Facing east puts east ahead");
        var far = RadarGeometry.project(100, 100, 0, 20, 100);
        check(far.clipped() && Math.hypot(far.x(), far.y()) <= 100.00001, "Distant radar points stay bounded");
        check(RadarGeometry.project(Double.NaN, 0, 0, 20, 100).clipped(), "Invalid geometry cannot escape the HUD");

        var pearl = ThrowAim.solve(0, -1.5, 20, 1.5, 0, 0.03, 0, 0, 0, 120);
        check(Double.isFinite(pearl.pitch()) && pearl.error() < 0.6, "Pearl aim accounts for gravity and drag");
        var potion = ThrowAim.solve(0, -1.5, 5, 0.5, -20, 0.05, 0, 0, 0, 60);
        check(potion.error() < 0.5, "Potion aim accounts for its launch angle and lower speed");
        var unreachable = ThrowAim.solve(0, 0, 1000, 1.5, 0, 0.03, 0, 0, 0, 120);
        check(unreachable.error() > 900, "Unreachable projectile destinations are rejected");

        // Synthetic registry components are explicit: no game resource reload or GPU is required.
        for (var item : List.of(Items.GOLDEN_APPLE, Items.DIAMOND_SWORD, Items.SPLASH_POTION, Items.POTION)) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).orElseThrow();
            if (!holder.areComponentsBound()) holder.bindComponents(
                net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
        check(PotionUse.healing(new ItemStack(Items.GOLDEN_APPLE)), "Golden apples are healing items");
        check(!PotionUse.healing(new ItemStack(Items.DIAMOND_SWORD)), "Non-healing items are never consumed");
        var heal = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
        var poison = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
        check(PotionUse.healing(heal) && !PotionUse.debuff(heal), "Healing splash potions are classified by effects");
        check(!PotionUse.healing(poison) && PotionUse.debuff(poison), "Harmful potions cannot be used for self-healing");
        check(!PotionUse.debuff(PotionContents.createItemStack(Items.POTION, Potions.POISON)), "Debuff helper cannot drink poison");
        heal.set(DataComponents.POTION_CONTENTS, heal.get(DataComponents.POTION_CONTENTS).withEffectAdded(new MobEffectInstance(MobEffects.POISON, 100)));
        check(!PotionUse.healing(heal), "Mixed harmful/healing custom potions are rejected for healing");
        var sources = new net.minecraft.world.SimpleContainer(2);
        sources.setItem(0, poison.copy()); sources.setItem(1, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        var inventory = new net.minecraft.world.SimpleContainer(1);
        var merge = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING); merge.setCount(63); inventory.setItem(0, merge);
        var slots = java.util.List.of(new net.minecraft.world.inventory.Slot(sources, 0, 0, 0), new net.minecraft.world.inventory.Slot(sources, 1, 0, 0), new net.minecraft.world.inventory.Slot(inventory, 0, 0, 0));
        var decision = com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.choose(slots, 2, stack -> true, index -> true);
        check(decision.slot() == 1 && decision.matching() == 2, "A non-fitting first stack cannot block a later compatible transfer");
        check(!com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.hasCapacity(poison, slots.subList(2, 3)), "Different potion components cannot be merged");
        merge.setCount(64);
        check(com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.choose(slots, 2, stack -> true, index -> true).slot() == -1, "Full inventory stops transfers");
        merge.setCount(63);
        check(com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.choose(slots, 2, stack -> true, index -> index != 1).waiting(), "Per-slot retry wait is distinguished from lack of capacity");
        var rejecting = new net.minecraft.world.inventory.Slot(new net.minecraft.world.SimpleContainer(1), 0, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        };
        check(!com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.hasCapacity(poison, java.util.List.of(rejecting)), "Empty restricted slots do not count as space");
        var limited = new net.minecraft.world.inventory.Slot(inventory, 0, 0, 0) {
            @Override public int getMaxStackSize(ItemStack stack) { return 16; }
        };
        check(!com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.hasCapacity(merge, java.util.List.of(limited)), "Container slot limits take priority over item stack limits");
        var include = com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.Filter.Include;
        var exclude = com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.Filter.Exclude;
        check(com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.matches(poison, java.util.List.of(Items.SPLASH_POTION), include)
            && !com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.matches(poison, java.util.List.of(Items.SPLASH_POTION), exclude), "Include/exclude filters agree on explicit lists");
        check(com.gaspoweredcake.client33.utils.player.ContainerTransferPlanner.matches(poison, java.util.List.of(), exclude), "An empty list preserves take-all behavior");
        var color = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(0, 400);
        check(color.r == 77 && color.g == 224 && color.b == 255 && color.a == 255, "Palette is bounded and deterministic");
        var wrap = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(-1, 100);
        check(wrap.r == color.r && wrap.g == color.g && wrap.b == color.b, "Palette wraps negative phases continuously");
        var left = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(0.33333, 100);
        var right = com.gaspoweredcake.client33.utils.render.EuphoricPalette.sample(0.33334, 100);
        check(Math.abs(left.r - right.r) <= 1 && Math.abs(left.g - right.g) <= 1 && Math.abs(left.b - right.b) <= 1, "Spectral transitions never flash at palette boundaries");
        var history = new com.gaspoweredcake.client33.utils.world.BlockObservationHistory<String, String>(2);
        history.observe("first", "diamond", 0); history.observe("second", "gold", 10);
        check(history.size() == 2, "Only actual observations populate ore history");
        history.observe("first", "diamond", 20); history.observe("third", "iron", 30);
        check(!history.snapshot().containsKey("second"), "Ore history evicts the oldest observation at capacity");
        history.expire(120, 100, key -> true);
        check(!history.snapshot().containsKey("first") && history.snapshot().containsKey("third"), "Rehidden memories expire at their last real observation boundary");
        history.forget("third"); check(history.size() == 0, "Server air/removal forgets mined blocks");
        history.observe("unloaded", "diamond", 200); history.expire(201, 100, key -> false);
        check(history.size() == 0, "Unloaded chunk observations are removed");
        history.observe("old world", "gold", 300); history.clear();
        check(history.size() == 0, "Ore observations never cross world sessions");
        var cardinal = com.gaspoweredcake.client33.utils.movement.FlightMotion.target(0, 1, 0, 1, 1, 0.5);
        near(cardinal.z, 1, "Forward flight follows the camera heading"); near(cardinal.y, 0.5, "Vertical speed is independent");
        var diagonal = com.gaspoweredcake.client33.utils.movement.FlightMotion.target(45, 1, 1, 0, 1, 0.5);
        near(diagonal.horizontalDistance(), 1, "Diagonal flight never exceeds horizontal speed");
        var accelerated = com.gaspoweredcake.client33.utils.movement.FlightMotion.approach(net.minecraft.world.phys.Vec3.ZERO, cardinal, 0.2);
        near(accelerated.horizontalDistance(), 0.2, "Flight acceleration is bounded per tick"); near(accelerated.y, 0.2, "Vertical acceleration is also bounded");
        var stopped = com.gaspoweredcake.client33.utils.movement.FlightMotion.approach(accelerated, net.minecraft.world.phys.Vec3.ZERO, 0.2);
        near(stopped.length(), 0, "Releasing movement brakes without overshooting");
        near(com.gaspoweredcake.client33.utils.movement.FlightMotion.approach(net.minecraft.world.phys.Vec3.ZERO, cardinal, 0).z, 1, "Zero acceleration preserves instant control");
        var recovery = new com.gaspoweredcake.client33.utils.movement.FlightRecovery();
        recovery.corrected(1000, 2000, 3);
        check(!recovery.allows(2999) && recovery.allows(3000), "Flight pauses until the correction deadline");
        recovery.corrected(3500, 2000, 3); recovery.corrected(6000, 2000, 3);
        check(recovery.held() && !recovery.allows(100000), "Repeated corrections hold controls instead of causing a setback loop");
        recovery.reset(); check(recovery.allows(0) && !recovery.held(), "Explicit resume and world cleanup release correction state");
        recovery.corrected(0, 1000, 2); recovery.corrected(10000, 1000, 2);
        check(!recovery.held(), "Old corrections leave the ten-second evidence window");
        System.out.println("Feature regressions passed.");
    }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-7, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
