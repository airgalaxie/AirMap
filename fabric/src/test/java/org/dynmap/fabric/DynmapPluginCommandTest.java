package org.dynmap.fabric;

import org.junit.jupiter.api.Test;
import net.minecraft.commands.Commands;
import org.dynmap.fabric.command.DynmapCommand;
import org.dynmap.fabric.command.DmapCommand;
import org.dynmap.fabric.command.DmarkerCommand;
import org.dynmap.fabric.command.DynmapExpCommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynmapPluginCommandTest {
    @Test
    void convertsAllAirMapCommandsToMinecraftCommandTrees() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Commands performs the same parse/suggestion tree conversion used during server startup.
        Commands commands = assertDoesNotThrow(() -> new Commands(dispatcher -> {
            new DynmapCommand(null).register(dispatcher);
            new DmapCommand(null).register(dispatcher);
            new DmarkerCommand(null).register(dispatcher);
            new DynmapExpCommand(null).register(dispatcher);
        }));
        assertNotNull(commands.suggestionBox());
        assertNotNull(commands.parseBox());
    }

    @Test
    void routesAirMapToTheSharedDynmapCommandImplementation() {
        assertEquals("dynmap", DynmapPlugin.coreCommandName("airmap"));
        assertEquals("dynmap", DynmapPlugin.coreCommandName("AirMap"));
    }

    @Test
    void leavesOtherCommandNamesUntouched() {
        assertEquals("dynmap", DynmapPlugin.coreCommandName("dynmap"));
        assertEquals("dmap", DynmapPlugin.coreCommandName("dmap"));
        assertEquals("dmarker", DynmapPlugin.coreCommandName("dmarker"));
        assertEquals("dynmapexp", DynmapPlugin.coreCommandName("dynmapexp"));
    }
}
