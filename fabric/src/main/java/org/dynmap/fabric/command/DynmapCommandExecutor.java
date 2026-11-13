package org.dynmap.fabric.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.suggestions.SuggestionProviders;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dynmap.fabric.DynmapPlugin;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.arguments.StringArgument.greedyString;
import static net.minecraft.commands.arguments.StringArgument.getString;

public class DynmapCommandExecutor implements Command<CommandSourceStack> {
    private final String cmd;
    private final DynmapPlugin plugin;
    private final String[] aliases;

    DynmapCommandExecutor(String cmd, DynmapPlugin plugin, String... aliases) {
        this.cmd = cmd;
        this.plugin = plugin;
        this.aliases = aliases;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, cmd);
        for (String alias : aliases) {
            register(dispatcher, alias);
        }
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootName) {
        final LiteralCommandNode<CommandSourceStack> command = LiteralArgumentBuilder.<CommandSourceStack>literal(rootName)
                .executes(this)
                .build();

        final ArgumentCommandNode<CommandSourceStack, String> args = RequiredArgumentBuilder.<CommandSourceStack, String>argument("args", greedyString())
                .suggests(SuggestionProviders.serverOnly(this::suggest))
                .executes(this)
                .build();

        command.addChild(args);
        dispatcher.getRoot().addChild(command);
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int start = context.getRange().getStart();
        String dynmapInput = context.getInput().substring(start);

        String[] args = dynmapInput.split("\\s+");
        plugin.handleCommand(context.getSource(), cmd, Arrays.copyOfRange(args, 1, args.length));
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(MinecraftServer server, CommandContext<? extends SuggestionContext> context, SuggestionsBuilder builder) {
        String rawArgs;
        try {
            rawArgs = getString(context, "args");
        } catch (IllegalArgumentException ignored) {
            rawArgs = "";
        }

        String[] args = rawArgs.isEmpty() ? new String[0] : rawArgs.split("\\s+", -1);
        int lastSeparator = Math.max(rawArgs.lastIndexOf(' '), rawArgs.lastIndexOf('\t'));
        SuggestionsBuilder currentArgBuilder = builder.createOffset(builder.getStart() + lastSeparator + 1);
        // Suggestion contexts are separate from execution sources in Minecraft 26.4.
        // Only player contexts can request these server-side completions.
        if (!(context.getSource().entity() instanceof ServerPlayer player)) {
            return currentArgBuilder.buildFuture();
        }
        for (String suggestion : plugin.getTabCompletions(player.createCommandSourceStack(), cmd, args)) {
            currentArgBuilder.suggest(suggestion);
        }

        return currentArgBuilder.buildFuture();
    }

    public String getUsage(CommandSourceStack commandSource) {
        return "Run /" + cmd + " help for details on using command";
    }
}
