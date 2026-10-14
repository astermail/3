package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.gui.runtime.RuntimeUi;
import com.gaspoweredcake.client33.gui.runtime.ThemeStyle;
import com.gaspoweredcake.client33.settings.BoolSetting;
import com.gaspoweredcake.client33.settings.DoubleSetting;
import com.gaspoweredcake.client33.systems.hud.elements.ModuleStatusHud;

public final class RuntimeUiRegression {
    public static void run() {
        var ui = new RuntimeUi();
        String source = "{ui.checkbox(\"Practice\", \"enabled\", \"Enable controls\", false)}\n"
            + "{ui.slider(\"Practice\", \"range\", \"Range\", 4, 1, 8, \"enabled\")}\n"
            + "{ui.textbox(\"Practice\", \"label\", \"Label\", \"Training\")}";
        ui.load(source);
        var group = ui.settings().getGroup("Practice");
        check(!group.get("range").isVisible(), "Disabled script parent hides its child slider");
        ((BoolSetting) group.get("enabled")).set(true);
        check(group.get("range").isVisible(), "Parent toggle reveals child without reloading script");
        ((DoubleSetting) group.get("range")).set(6.0);
        ui.load(source);
        check(ui.value("Practice", "range").getNumber() == 6, "Reload preserves matching control values");
        check(ui.settings().getGroup("Practice").get("enabled").title.equals("Enable controls"), "Script labels remain separate from stable keys");
        var before = ui.settings(); long version = ui.revision();
        for (String invalid : new String[]{"{ui.slider(\"Bad\", \"x\", \"X\", 5, 10, 1)}", source + source, "{ui.textbox(\"x\", \"bad/key\", \"Key\", \"\")}", "{ui.checkbox(\"x\")}"}) {
            boolean rejected = false;
            try { ui.load(invalid); } catch (RuntimeException expected) { rejected = true; }
            check(rejected && ui.settings() == before && ui.revision() == version, "Failed declarations do not partially replace live UI");
        }
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 257; i++) many.append("{ui.checkbox(\"Load\", \"field").append(i).append("\", \"Field\", false)}\n");
        boolean bounded = false;
        try { ui.load(many.toString()); } catch (RuntimeException expected) { bounded = true; }
        check(bounded && ui.settings() == before, "Widget count is bounded and overflow is transactional");
        var ss = new org.meteordev.starscript.Starscript(); ui.exposeValues(ss);
        var parsed = org.meteordev.starscript.compiler.Parser.parse("{ui.value(\"Practice\", \"range\")}");
        check(ss.run(org.meteordev.starscript.compiler.Compiler.compile(parsed)).toString().equals("6"), "Existing Starscript expressions read live UI values");

        String style = "{\"scale\":1,\"fontScale\":0.9,\"rounding\":0,\"gradient\":true,\"accent\":[224,48,54,255],\"gradientEnd\":[130,90,245,255],\"border\":[60,60,66,255],\"surface\":[20,20,23,255]}";
        check(ThemeStyle.parse(style).rounding() == 0 && ThemeStyle.parse(style).fontScale() == .9, "Theme JSON supports sharp corners and independent text scaling");
        for (String invalid : new String[]{style.replace("0.9", "8"), style.replace("224,48", "999,48"), style.replace("\"rounding\":0", "\"rounding\":-1")}) {
            boolean rejected = false; try { ThemeStyle.parse(invalid); } catch (RuntimeException expected) { rejected = true; }
            check(rejected, "Invalid style values are rejected before application");
        }
        for (double scale : new double[]{.5, .75, 1, 1.5, 2, 3}) {
            int raster = com.gaspoweredcake.client33.systems.hud.HudTextMetrics.rasterHeight(scale);
            double factor = com.gaspoweredcake.client33.systems.hud.HudTextMetrics.drawScale(scale, raster);
            check(Math.abs(raster * factor - 18 * scale) < 0.0001, "HUD font height scales linearly, not quadratically");
            check(raster >= 18 * scale, "HUD glyphs are sampled at or above display resolution");
        }
        double horizontal = com.gaspoweredcake.client33.utils.render.EspRange.squared(10, -124, 10, true);
        double spherical = com.gaspoweredcake.client33.utils.render.EspRange.squared(10, -124, 10, false);
        check(horizontal < 64 * 64 && spherical > 64 * 64, "Full-height ESP includes below-deepslate targets within horizontal range");
        check(com.gaspoweredcake.client33.utils.storage.StorageRenderMath.opacity(Math.sqrt(spherical), Math.sqrt(horizontal), 2.5, 64, 12) == 1, "Deep containers do not fade because of vertical separation");
        var underground = com.gaspoweredcake.client33.utils.storage.StorageRenderMath.bounds(new net.minecraft.core.BlockPos(10, -60, 10), null, true);
        check(underground.minY == -60 && underground.maxY < -59, "Chest bounds retain negative build heights");
        var panel = new ModuleStatusHud(); panel.resizePanel(320, 100);
        check(panel.getWidth() == 320 && panel.getHeight() == 100, "Status panels support explicit resizing");
        panel.pinned = true; panel.resizePanel(700, 300); panel.move(10, 20); panel.setPos(40, 50);
        check(panel.getWidth() == 320 && panel.x == 0 && panel.y == 0, "Pin prevents movement and resizing");
        check(panel.toTag().getBooleanOr("pinned", false), "Pin state is persisted");
        System.out.println("Runtime UI transactions, theme files and pinned panel regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
