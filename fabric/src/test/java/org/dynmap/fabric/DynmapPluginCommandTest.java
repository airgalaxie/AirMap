package org.dynmap.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynmapPluginCommandTest {
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
