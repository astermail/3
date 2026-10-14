/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.macros;

import com.gaspoweredcake.client33.gui.utils.StarscriptTextBoxRenderer;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.utils.misc.ISerializable;
import com.gaspoweredcake.client33.utils.misc.Keybind;
import com.gaspoweredcake.client33.utils.misc.Client33Starscript;
import com.gaspoweredcake.client33.utils.player.ChatUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.meteordev.starscript.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.gaspoweredcake.client33.Client33.mc;

public class Macro implements ISerializable<Macro> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the macro.")
        .build()
    );

    public Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("The messages for the macro to send.")
        .onChanged(_ -> dirty = true)
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    public Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("The bind to run the macro.")
        .build()
    );

    public Setting<net.minecraft.world.item.Item> hotbarItem = sgGeneral.add(new ItemSetting.Builder()
        .name("hotbar-item").description("Select this item from your hotbar before running messages; Air leaves your slot unchanged.").defaultValue(net.minecraft.world.item.Items.AIR).build());

    private final List<Script> scripts = new ArrayList<>(1);
    private boolean dirty;

    public Macro() {
    }

    public Macro(Tag tag) {
        fromTag((CompoundTag) tag);
    }

    public boolean onAction(boolean isKey, int value, int modifiers) {
        if (!keybind.get().matches(isKey, value, modifiers) || mc.gui.screen() != null) return false;
        return onAction();
    }

    public boolean onAction() {
        if (mc.player == null) return false;
        if (hotbarItem.get() != net.minecraft.world.item.Items.AIR) {
            var found = com.gaspoweredcake.client33.utils.player.InvUtils.find(stack -> stack.is(hotbarItem.get()), 0, 8);
            if (!found.found()) { ChatUtils.warning("Macro item is not in your hotbar."); return false; }
            com.gaspoweredcake.client33.utils.player.InvUtils.swap(found.slot(), false);
        }
        if (dirty) {
            scripts.clear();

            for (String message : messages.get()) {
                Script script = Client33Starscript.compile(message);
                if (script != null) scripts.add(script);
            }

            dirty = false;
        }

        for (Script script : scripts) {
            String message = Client33Starscript.run(script);

            if (message != null) {
                ChatUtils.sendPlayerMsg(message, false);
            }
        }

        return true;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public Macro fromTag(CompoundTag tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompoundOrEmpty("settings"));
        }

        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Macro macro = (Macro) o;
        return Objects.equals(macro.name.get(), this.name.get());
    }
}
