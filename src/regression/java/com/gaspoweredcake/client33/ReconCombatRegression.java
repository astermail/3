package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.combat.ServerTickEstimator;
import com.gaspoweredcake.client33.utils.recon.ReconParser;
import com.gaspoweredcake.client33.utils.recon.MovementHeat;
import java.util.UUID;
import java.util.List;
import com.gaspoweredcake.client33.utils.combat.ShieldEstimate;
import com.gaspoweredcake.client33.utils.combat.ExplosionRisk;

/** Offline scenarios for protocol estimates and untrusted, server-specific text. */
public final class ReconCombatRegression {
    public static void run() {
        ServerTickEstimator clock = new ServerTickEstimator();
        check(clock.ageMillis(1000) == Long.MAX_VALUE, "No packet age exists before the first sample");
        clock.attack(1000);
        check(!clock.ready(1499, 10, 0), "Fallback must not allow early attacks");
        check(clock.ready(1500, 10, 0), "20 TPS fallback cooldown");
        clock.sample(0, 0);
        clock.sample(20, 2000);
        check(clock.sampled() && clock.tickMillis() == 100, "Server time delta must detect 10 TPS");
        check(clock.ageMillis(2200) == 200, "Packet age uses monotonic receipt time");
        clock.attack(2000);
        check(!clock.ready(3000, 10, 25), "Jitter and configured margin must delay attacks");
        check(clock.ready(3050, 10, 25), "Cooldown expires with margin");
        clock.sample(19, 2100);
        check(clock.tickMillis() == 100, "Out-of-order time cannot reset the model");
        clock.sample(20, 9000);
        check(!clock.ready(9000, 10, 0), "Duplicate/stalled server tick cannot refresh readiness");
        check(clock.readyWithFallback(9000, 10, 25), "Stale time samples cannot permanently disable attacks with live network traffic");
        clock.attack(9000);
        check(!clock.readyWithFallback(9524, 10, 25), "Fallback preserves weapon cooldown and configured margin");
        check(clock.readyWithFallback(9525, 10, 25), "Fallback resumes at the cooldown boundary");
        check(!clock.readyWithFallback(10000, Double.NaN, 0), "Fallback rejects invalid weapon timing");
        check(!clock.ready(9000, Double.NaN, 0), "Invalid cooldown cannot pass");
        check(!clock.ready(3050, 10, Double.NaN), "Invalid margin cannot bypass cooldown");
        clock.reset();
        check(!clock.sampled() && clock.tickMillis() == 50 && clock.ready(10000, 10, 0), "Disconnect clears attack and timing history");

        check(!clock.ready(10000, Double.NaN, 0), "Invalid cooldown rejected before any attack");
        check(ShieldEstimate.ready(2, 5, 300, 150, 50), "Half-ping predicts shield activation at delay boundary");
        check(!ShieldEstimate.ready(2, 5, 300, 50, 50), "Prediction lead is capped");
        check(!ShieldEstimate.ready(5, 5, 0, 150, 100), "Client use ticks cannot masquerade as server ticks under lag");
        check(ShieldEstimate.ready(10, 5, 0, 150, 100), "Shield delay follows estimated server TPS");

        check(ExplosionRisk.safeSelf(5, 6, 20, 2, true), "A survivable crystal remains eligible");
        check(!ExplosionRisk.safeSelf(6, 6, 8, 2, true), "Reserve boundary must reject an explosion");
        check(!ExplosionRisk.safeSelf(7, 6, 40, 0, false), "Self-damage cap still applies without anti-suicide");
        check(!ExplosionRisk.safeSelf(Double.NaN, 6, 20, 2, true), "Invalid prediction cannot bypass self protection");
        check(!ExplosionRisk.safeFriend(4, 6, 4), "Friend lethality overrides the numerical damage cap");
        check(ExplosionRisk.advantage(12, 4, 8) && !ExplosionRisk.advantage(11, 4, 8), "Damage advantage boundary");
        check(!ExplosionRisk.advantage(Double.NaN, 4, 0), "Invalid target damage remains ineligible with filter disabled");

        ReconParser parser = new ReconParser();
        parser.accept("§aClaim [-2, 3]: Knights", ReconParser.Source.Chat, 1000);
        check(parser.claims().size() == 1 && parser.claims().iterator().next().cell().x() == -2, "Negative chunk coordinates and formatting");
        parser.accept("<Griefer> Claim [1, 1]: Fake", ReconParser.Source.Chat, 1000);
        parser.accept("Claim [99999999999999, 1]: Fake", ReconParser.Source.Tab, 1000);
        check(parser.claims().size() == 1, "No player-chat prefix or out-of-bounds claim inference");
        parser.accept("Knights Power: 20/40", ReconParser.Source.Tab, 2000);
        parser.accept("Faction: Knights", ReconParser.Source.Chat, 2100);
        parser.accept("Power: 18/40", ReconParser.Source.Chat, 2200);
        var readings = parser.powers().get("knights");
        check(readings.size() == 2 && ReconParser.newest(readings.values()).current() == 18, "Preserve disagreements instead of averaging independent sources");
        parser.accept("Power: 1/40", ReconParser.Source.Scoreboard, 2300);
        check(readings.size() == 2, "Faction context cannot bleed between sources");
        parser.accept("Power: 1/40", ReconParser.Source.Chat, 9000);
        check(readings.get(ReconParser.Source.Chat).current() == 18, "Old /f who context expires");
        check(!parser.configure("[", ReconParser.POWER), "Invalid regex rejected");
        check(!parser.configure("(x)", ReconParser.POWER), "Incorrect capture count rejected");
        parser.accept("Faction Map [-2, 3]", ReconParser.Source.Chat, 10000);
        parser.accept("|AA.|", ReconParser.Source.Chat, 10001);
        parser.accept("|A+B|", ReconParser.Source.Chat, 10002);
        parser.accept("Legend: A=Knights,B=Rivals", ReconParser.Source.Chat, 10003);
        check(parser.claims().stream().anyMatch(c -> c.cell().equals(new ReconParser.Cell(-1, 3)) && c.owner().equals("Rivals")), "Map origin and east/west alignment");
        check(parser.claims().stream().anyMatch(c -> c.cell().equals(new ReconParser.Cell(-3, 2))), "Map north is decreasing chunk Z");
        var oldSidebar = List.of("Faction: Knights", "Power: 18/40", "Time: 1");
        var changed = ReconParser.changedLines(oldSidebar, List.of("Faction: Knights", "Power: 18/40", "Time: 2"));
        check(!changed.contains("Power: 18/40"), "An unrelated scoreboard update cannot refresh stale power");
        changed = ReconParser.changedLines(oldSidebar, List.of("Faction: Rivals", "Power: 18/40", "Time: 2"));
        check(changed.contains("Power: 18/40"), "A changed faction context must capture its own power value");
        parser.expire(20000, 5000);
        check(parser.claims().isEmpty() && parser.powers().isEmpty(), "All old observations expire");
        parser.clear();
        parser.accept("Faction Map [0, 0]", ReconParser.Source.Chat, 30000);
        parser.accept("|A++|", ReconParser.Source.Chat, 30001);
        parser.accept("Legend: A=Bad", ReconParser.Source.Chat, 30002);
        check(parser.claims().isEmpty(), "Ambiguous map origin rejected");
        for (int i = 0; i < 2100; i++) parser.accept("Claim [" + i + ", 0]: Test", ReconParser.Source.Chat, i);
        check(parser.claims().size() == 2048, "Claim storage stays bounded");

        parser.clear();
        check(parser.configureMap("^Map (-?[0-9]+) (-?[0-9]+)$", "^Row (.+)$", "([A-Z]):([A-Za-z]+)"), "Custom map grammar accepted");
        parser.accept("Map 10 -5", ReconParser.Source.Chat, 40000);
        parser.accept("Row +A", ReconParser.Source.Chat, 40001);
        parser.accept("A:Knights", ReconParser.Source.Chat, 40002);
        check(parser.claims().stream().anyMatch(c -> c.cell().equals(new ReconParser.Cell(11, -5))), "Configured map coordinates and legend");
        check(!parser.configureMap("([", "(x)", "(x)(y)"), "Malformed map grammar rejected");
        parser.clear();
        check(parser.configureMap("^(x)?(y)?$", "^(z)?$", "(a)?(b)?"), "Optional captures compile");
        parser.accept("", ReconParser.Source.Chat, 41000);
        check(parser.claims().isEmpty(), "Absent configured captures cannot fabricate claims");
        parser.configureMap(ReconParser.MAP_HEADER, ReconParser.MAP_ROW, ReconParser.MAP_LEGEND);
        parser.accept("Faction Map [1875000, 0]", ReconParser.Source.Chat, 42000);
        parser.accept("|+A|", ReconParser.Source.Chat, 42001);
        parser.accept("Legend: A=Outside", ReconParser.Source.Chat, 42002);
        check(parser.claims().isEmpty(), "Map offsets cannot cross coordinate bounds");

        MovementHeat heat = new MovementHeat(2);
        UUID player = new UUID(0, 1);
        heat.sample(player, -1, 64, -1, 0, 8, 1000);
        heat.sample(player, -1, 64, -1, 500, 8, 1000);
        check(heat.cells().isEmpty(), "Stationary observations do not accumulate heat");
        heat.sample(player, -2, 64, -1, 1000, 8, 1000);
        var cell = heat.cells().iterator().next();
        check(cell.cell().x() == -1, "Negative coordinates must floor, not truncate");
        check(Math.abs(MovementHeat.decayed(cell, 2000, 1000) - 0.5) < 1e-9, "Heat halves at configured half-life");
        heat.sample(player, 1000, 64, 1000, 1500, 8, 1000);
        check(heat.cells().size() == 1, "Teleport cannot fabricate a movement hotspot");
        heat.sample(player, 1001, 64, 1000, 2000, 8, 1000);
        heat.sample(player, 1010, 64, 1000, 2500, 8, 1000);
        check(heat.cells().size() == 2, "Heatmap evicts old cells at capacity");
        heat.expire(10000, 1000);
        check(heat.cells().isEmpty(), "Movement history expires");
        System.out.println("Recon and combat regressions passed.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
