package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldAliasTest {
    private static List<String> aliases(String canonicalId) {
        return DynmapWorld.getVanillaLegacyAliases(canonicalId);
    }

    @Test
    void vanillaOverworldGetsOverworldLegacyAliases() {
        assertEquals(Arrays.asList("world"), aliases("minecraft:overworld"));
    }

    @Test
    void vanillaNetherGetsNetherLegacyAliases() {
        assertEquals(Arrays.asList("DIM-1", "nether"), aliases("minecraft:the_nether"));
    }

    @Test
    void vanillaEndGetsEndLegacyAliases() {
        assertEquals(Arrays.asList("DIM1", "the_end"), aliases("minecraft:the_end"));
    }

    @Test
    void customOverworldGetsNoVanillaLegacyAliases() {
        assertTrue(aliases("custom:overworld").isEmpty());
    }

    @Test
    void customEndGetsNoVanillaLegacyAliases() {
        assertTrue(aliases("custom:the_end").isEmpty());
    }

    @Test
    void ordinaryMinecraftDimensionGetsNoVanillaLegacyAliases() {
        assertTrue(aliases("minecraft:farmglueck").isEmpty());
    }
}