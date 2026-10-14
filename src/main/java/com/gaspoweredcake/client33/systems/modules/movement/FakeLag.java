package com.gaspoweredcake.client33.systems.modules.movement;

public class FakeLag extends MovementDelay {
    public FakeLag() { super("fake-lag", "Simulates latency by delaying movement; actions preserve packet order and keepalives remain immediate.", "FakeLag"); }
    @Override protected boolean shouldDelay() { return mc.player != null && mc.gui.screen() == null && mc.player.isAlive(); }
}
