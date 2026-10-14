package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;

public class HitColor extends Module {
    public final Setting<SettingColor> color = settings.getDefaultGroup().add(new ColorSetting.Builder().name("color").description("Tint applied to damaged living entities.").defaultValue(new SettingColor(255, 75, 110)).build());
    public HitColor() { super(Categories.Render, "hit-color", "Changes the visible damage tint of living entities.", "Hit Color"); }
}
