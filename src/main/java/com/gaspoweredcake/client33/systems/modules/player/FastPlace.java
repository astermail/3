package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;

public class FastPlace extends Module {
    public final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder().name("delay-ticks").description("Client delay between block placements; server rules still apply.").defaultValue(1).range(0, 4).build());
    public FastPlace() { super(Categories.Player, "fast-place", "Lowers the block placement delay without accelerating other items.", "FastPlace"); }
}
