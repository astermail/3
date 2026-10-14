/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class ModuleArgumentType implements ArgumentType<Module> {
    private static final ModuleArgumentType INSTANCE = new ModuleArgumentType();
    private static final DynamicCommandExceptionType NO_SUCH_MODULE = new DynamicCommandExceptionType(name -> Component.literal("Module with name " + name + " doesn't exist."));

    private static final Collection<String> EXAMPLES = Modules.get().getAll()
        .stream()
        .limit(3)
        .map(module -> module.name)
        .toList();

    public static ModuleArgumentType create() {
        return INSTANCE;
    }

    public static Module get(CommandContext<?> context) {
        return context.getArgument("module", Module.class);
    }

    private ModuleArgumentType() {
    }

    @Override
    public Module parse(StringReader reader) throws CommandSyntaxException {
        String argument = reader.readString();
        Module module = Modules.get().get(argument);
        if (module == null) throw NO_SUCH_MODULE.create(argument);

        return module;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Modules.get().getAll().stream().flatMap(module -> java.util.stream.Stream.concat(java.util.stream.Stream.of(module.name), java.util.Arrays.stream(module.aliases).map(Modules::normalizeName))), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
