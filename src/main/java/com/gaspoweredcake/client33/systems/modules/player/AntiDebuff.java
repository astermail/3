package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class AntiDebuff extends Module {
    public AntiDebuff() { super(Categories.Player, "anti-debuff", "Suppresses harmful effects on your local player. The server can still apply damage and enforce effects.", "AntiDebuff"); }
    public boolean blocks(MobEffect effect) { return isActive() && effect.getCategory() == MobEffectCategory.HARMFUL; }
}
