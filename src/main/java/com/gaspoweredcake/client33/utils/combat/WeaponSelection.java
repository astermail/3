package com.gaspoweredcake.client33.utils.combat;

import java.util.List;

/** Selects a usable candidate, with preference applying only when that weapon is present. */
public final class WeaponSelection {
    public enum Kind { Sword, Axe, Mace, Spear, Trident, Any }
    public record Candidate(int slot, Kind kind, double damage) {}
    private WeaponSelection() {}

    public static int choose(List<Candidate> candidates, Kind preferred, double threshold, int currentSlot) {
        Candidate best = null, preference = null;
        for (Candidate candidate : candidates) {
            if (!Double.isFinite(candidate.damage()) || candidate.damage() < 0) continue;
            if (better(candidate, best, currentSlot)) best = candidate;
            if (candidate.kind() == preferred && better(candidate, preference, currentSlot)) preference = candidate;
        }
        if (best == null) return currentSlot;
        if (preferred == Kind.Any || preference == null || best.kind() == preferred) return best.slot();
        return best.damage() - preference.damage() >= Math.max(0, threshold) ? best.slot() : preference.slot();
    }

    private static boolean better(Candidate candidate, Candidate previous, int currentSlot) {
        return previous == null || candidate.damage() > previous.damage()
            || candidate.damage() == previous.damage() && candidate.slot() == currentSlot;
    }
}
