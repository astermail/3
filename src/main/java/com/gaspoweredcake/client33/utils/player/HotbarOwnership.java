package com.gaspoweredcake.client33.utils.player;

/** Immutable identity for a use session. A manual slot change relinquishes restoration rights. */
public record HotbarOwnership(Object player, Object world, int original, int selected) {
    public boolean sameSession(Object player, Object world) { return this.player == player && this.world == world; }
    public boolean mayStart(Object player, Object world, int currentSlot) { return sameSession(player, world) && currentSlot == original; }
    public boolean mayRestore(Object player, Object world, int currentSlot) { return sameSession(player, world) && currentSlot == selected; }
}
