package org.dynmap.bukkit;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.common.DynmapCommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("UnstableApiUsage")
public class CommandHandler implements BasicCommand {
	private final DynmapPlugin plugin;
	private final String command;

	public CommandHandler(DynmapPlugin plugin, String command) {
		this.plugin = plugin;
		this.command = command;
	}

	@Override
	public void execute(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
		DynmapCommandSender dsender;
		CommandSender sender = commandSourceStack.getSender();

		plugin.getLogger().info(Arrays.toString(args));

        if(sender instanceof Player) {
            dsender = plugin.new BukkitPlayer((Player)sender);
        } else {
            dsender = plugin.new BukkitCommandSender(sender);
        }

        if (plugin.core != null) {
        	plugin.core.processCommand(dsender, command, command, args);
		}
	}

	@Override
	public @NotNull Collection<String> suggest(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
		DynmapCommandSender dsender;
		CommandSender sender = commandSourceStack.getSender();

		plugin.getLogger().info(Arrays.toString(args));

        if(sender instanceof Player) {
            dsender = plugin.new BukkitPlayer((Player)sender);
        }
        else {
            dsender = plugin.new BukkitCommandSender(sender);
        }

        if (plugin.core != null)
        	return plugin.core.getTabCompletions(dsender, command, args);
        else
        	return Collections.emptyList();
	}
}
