package com.gaspoweredcake.client33.utils.world;

/** Availability for one schematic session; paused builds still own the builder. */
public record SchematicBuildState(boolean inWorld, boolean loading, boolean ready, boolean active, boolean paused) {
    public boolean canLoad() { return inWorld && !loading && !active; }
    public boolean canPlace() { return inWorld && ready && !loading && !active; }
    public boolean canPause() { return inWorld && active && !paused; }
    public boolean canResume() { return inWorld && active && paused; }
    public boolean canStop() { return inWorld && active; }
}
