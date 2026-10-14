package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.combat.AttackCadence;

public final class KillAuraRegression {
    public static void run() {
        var cadence = new AttackCadence();
        check(cadence.ready(1000, 1, 12.5, 50, 25), "First fully charged sword attack is available");
        check(!cadence.ready(1000, 0.99, 12.5, 50, 25), "Partial charge never attacks, including custom-delay mode");
        cadence.record(1000);
        for (int i = 0; i < 10; i++) check(!cadence.ready(1000, 1, 12.5, 50, 25), "Repeated rotation callbacks cannot spend the same charge twice");
        check(!cadence.ready(1649, 1, 12.5, 50, 25), "Sword observes recharge plus margin");
        check(cadence.ready(1650, 1, 12.5, 50, 25), "Sword resumes at its actual charge boundary");
        check(!cadence.ready(1650, 1, 20, 50, 25), "Switching to a slower weapon cannot reuse the fast interval");
        check(cadence.ready(2025, 1, 20, 50, 25), "Slow weapon resumes at its own boundary");
        check(!cadence.ready(2025, 1, 12.5, 100, 25), "Ten TPS doubles server weapon recharge");
        check(cadence.ready(2275, 1, 12.5, 100, 25), "Lag-aware cadence resumes without a burst");
        cadence.record(9000);
        check(!cadence.ready(8000, 1, 12.5, 50, 0), "Clock rollback cannot make an attack ready");
        check(!cadence.ready(10000, 1, Double.NaN, 50, 0), "Invalid weapon speed cannot cause a spam loop");
        cadence.reset();
        check(!cadence.ready(0, 0, 12.5, 50, 0), "Enabling the module never bypasses a partially charged weapon");
        int attacks = 0;
        for (long now = 0; now <= 5000; now += 50) {
            if (cadence.ready(now, 1, 12.5, 50, 25)) { cadence.record(now); attacks++; }
        }
        check(attacks == 8, "Five seconds of eligible callbacks yields eight spaced sword attacks, not tick spam");
        System.out.println("KillAura weapon recharge regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
