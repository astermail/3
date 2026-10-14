/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Invoker("isSprintingPossible")
    boolean client33$isSprintingPossible(boolean flying);

    @Invoker("isSlowDueToUsingItem")
    boolean client33$isSlowDueToUsingItem();

    @Accessor("positionReminder")
    void client33$setPositionReminder(int ticks);
}
