package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MinecraftModelLoaderTest {
    @Test
    void resolvesMinecraftTextureSlotsWithAndWithoutLegacyHashPrefix() {
        Map<String, String> textures = Map.of(
                "all", "minecraft:block/heavy_core",
                "side", "#all");

        assertEquals("minecraft:block/heavy_core", MinecraftModelLoader.dereference("all", textures));
        assertEquals("minecraft:block/heavy_core", MinecraftModelLoader.dereference("#all", textures));
        assertEquals("minecraft:block/heavy_core", MinecraftModelLoader.dereference("side", textures));
        assertEquals("minecraft:block/stone", MinecraftModelLoader.dereference("minecraft:block/stone", textures));
    }
}
