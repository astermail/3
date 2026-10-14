/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.pathing;

import baritone.api.BaritoneAPI;
import com.gaspoweredcake.client33.utils.misc.TemporaryOverrides;

/** Avoid risky movement and frequent vertical travel while the builder is active. */
public final class BaritoneBuildGuard {
    private TemporaryOverrides overrides;

    public void update(boolean enabled, boolean building) {
        if (!enabled || !building) { release(); return; }
        if (overrides != null) return;
        overrides = new TemporaryOverrides();
        var settings = BaritoneAPI.getSettings();
        set(settings.allowSprint, false);
        set(settings.allowParkour, false);
        set(settings.allowParkourPlace, false);
        set(settings.allowDiagonalDescend, false);
        set(settings.allowOvershootDiagonalDescend, false);
        set(settings.maxFallHeightNoWater, Math.min(settings.maxFallHeightNoWater.value, 1));
        set(settings.maxFallHeightBucket, Math.min(settings.maxFallHeightBucket.value, 1));
        set(settings.buildInLayers, true);
        set(settings.layerHeight, 1);
        set(settings.layerOrder, false);
    }

    private <T> void set(baritone.api.Settings.Setting<T> setting, T value) {
        overrides.apply(() -> setting.value, changed -> setting.value = changed, value);
    }

    public void release() {
        if (overrides == null) return;
        overrides.close();
        overrides = null;
    }

    public void withOriginals(Runnable action) {
        if (overrides == null) action.run();
        else overrides.withOriginals(action);
    }
}
