package org.dynmap.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * Entry points called by AirMap's Minecraft mixins.
 */
public final class MinecraftAdapterHooks {
    private MinecraftAdapterHooks() {
    }

    public static void serverStarting(MinecraftServer server) {
        DynmapPlugin plugin = DynmapMod.plugin;
        if (plugin != null) {
            plugin.serverStart(server);
        }
    }

    public static void serverStarted(MinecraftServer server) {
        DynmapPlugin plugin = DynmapMod.plugin;
        if (plugin != null) {
            plugin.serverStarted(server);
        }
    }

    public static void endServerTick(MinecraftServer server) {
        DynmapPlugin plugin = DynmapMod.plugin;
        if (plugin != null) {
            plugin.serverTick(server);
        }
    }

    public static void serverStopping(MinecraftServer server) {
        DynmapPlugin plugin = DynmapMod.plugin;
        if (plugin != null) {
            plugin.serverStop(server);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        DynmapPlugin plugin = DynmapMod.plugin;
        if (plugin != null) {
            plugin.registerCommands(dispatcher);
        }
    }
}
