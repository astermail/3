package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.gui.utils.FeatureSearch;
import com.gaspoweredcake.client33.utils.player.TeleportRequest;

public final class SearchRegression {
    public static void run() {
        check(FeatureSearch.matches("base finder", "stash-finder", "Base Finder"), "Common base finder alias is discoverable");
        check(FeatureSearch.matches("RTP", "random-teleport", "RTP"), "RTP is discoverable");
        check(FeatureSearch.matches("chest_esp", "storage-esp", "Chest ESP"), "Separators do not hide aliases");
        check(FeatureSearch.matches("cooldown aura", "Kill Aura", "Weapon cooldown timing"), "Words can match different indexed fields");
        check(FeatureSearch.matches("server cooldown", "server-cooldown"), "Raw setting names are searchable");
        check(!FeatureSearch.matches("unimplemented feature", "Stash Finder", "Base Finder"), "Unrelated features do not match");
        check(!FeatureSearch.matches("flight", "Changes the level of light"), "Search does not join description words into unrelated flight matches");
        check(FeatureSearch.matches("flight", "Smooth flight controls"), "Flight description remains searchable");
        check(FeatureSearch.rank("basefinder", "stash-finder", "Stash Finder", new String[]{"Base Finder"}) == 0, "Exact aliases rank first");
        check(TeleportRequest.command(" /rtp ").equals("rtp"), "Slash-prefixed commands normalize");
        check(TeleportRequest.command("wild world").equals("wild world"), "Server command arguments are retained");
        for (String invalid : new String[]{"", "/", "//rtp", "rtp\nkill", "r".repeat(257)}) {
            try { TeleportRequest.command(invalid); throw new AssertionError("Invalid command accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("Feature search and RTP command regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
