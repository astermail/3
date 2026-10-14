package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.player.ClickCadence;

public final class AutoClickerRegression {
    public static void run() {
        var cadence = new ClickCadence();
        check(cadence.tick(true, true, 2), "First eligible click is immediate");
        check(!cadence.tick(true, true, 2), "Respect intervening tick");
        check(cadence.tick(true, true, 2), "Two-tick interval has no extra tick");
        for (int i = 0; i < 30; i++) check(!cadence.tick(true, false, 2), "Cooldown/target/use gating suppresses attacks");
        check(cadence.tick(true, true, 2), "Ready attack fires after blocked interval");
        check(!cadence.tick(true, true, 2), "No catch-up burst");
        check(!cadence.tick(false, true, 2), "Released button suppresses clicks");
        check(cadence.tick(true, true, 2), "New press resets cadence");
        cadence.reset();
        for (int i = 0; i < 3; i++) check(cadence.tick(true, true, 0), "Zero delay permits one click each tick");
        System.out.println("Auto Clicker cadence regressions passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
