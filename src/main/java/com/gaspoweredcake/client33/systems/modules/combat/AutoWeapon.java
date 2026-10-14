/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.combat;

import com.gaspoweredcake.client33.events.entity.player.AttackEntityEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.entity.DamageUtils;
import com.gaspoweredcake.client33.utils.combat.WeaponSelection;
import com.gaspoweredcake.client33.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import java.util.ArrayList;

public class AutoWeapon extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("weapon")
        .description("Preferred melee weapon. Any selects the greatest estimated charged damage.")
        .defaultValue(Weapon.Sword)
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("If the non-preferred weapon produces this much damage this will favor it over your preferred weapon.")
        .defaultValue(4)
        .min(0)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Prevents you from breaking your weapon.")
        .defaultValue(false)
        .build()
    );

    public AutoWeapon() {
        super(Categories.Combat, "auto-weapon", "Finds the best weapon to use in your hotbar.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player != null && mc.level != null && event.entity instanceof LivingEntity livingEntity) {
            InvUtils.swap(getBestWeapon(livingEntity), false);
        }
    }

    private int getBestWeapon(LivingEntity target) {
        var candidates = new ArrayList<WeaponSelection.Candidate>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || antiBreak.get() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 10) continue;
            WeaponSelection.Kind kind = kind(stack);
            if (kind == null || !mc.player.isWithinAttackRange(stack, target.getBoundingBox(), 0)) continue;
            candidates.add(new WeaponSelection.Candidate(i, kind, DamageUtils.getChargedAttackDamage(mc.player, target, stack)));
        }
        return WeaponSelection.choose(candidates, WeaponSelection.Kind.valueOf(weapon.get().name()), threshold.get(), mc.player.getInventory().getSelectedSlot());
    }

    private static WeaponSelection.Kind kind(ItemStack stack) {
        if (stack.is(ItemTags.SWORDS)) return WeaponSelection.Kind.Sword;
        if (stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem) return WeaponSelection.Kind.Axe;
        if (stack.is(ItemTags.SPEARS)) return WeaponSelection.Kind.Spear;
        if (stack.getItem() instanceof MaceItem) return WeaponSelection.Kind.Mace;
        if (stack.getItem() instanceof TridentItem) return WeaponSelection.Kind.Trident;
        return null;
    }

    public enum Weapon {
        Sword,
        Axe,
        Mace,
        Spear,
        Trident,
        Any
    }
}
