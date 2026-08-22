package org.dynmap.fabric_26_1_2.command;

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
import org.dynmap.fabric_26_1_2.DynmapPlugin;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

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
                .suggests(this::suggest)
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

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String rawArgs;
        try {
            rawArgs = getString(context, "args");
        } catch (IllegalArgumentException ignored) {
            rawArgs = "";
        }

        String[] args = rawArgs.isEmpty() ? new String[0] : rawArgs.split("\\s+", -1);
        int lastSeparator = Math.max(rawArgs.lastIndexOf(' '), rawArgs.lastIndexOf('\t'));
        SuggestionsBuilder currentArgBuilder = builder.createOffset(builder.getStart() + lastSeparator + 1);
        for (String suggestion : plugin.getTabCompletions(context.getSource(), cmd, args)) {
            currentArgBuilder.suggest(suggestion);
        }

        return currentArgBuilder.buildFuture();
    }

    public String getUsage(CommandSourceStack commandSource) {
        return "Run /" + cmd + " help for details on using command";
    }
}
