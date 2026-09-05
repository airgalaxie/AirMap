package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.util.List;
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

    @Test
    void derivesStaticLayerNamesFromMinecraftModelData() {
        var chest = JsonParser.parseString("{\"type\":\"minecraft:chest\",\"texture\":\"normal\"}")
                .getAsJsonObject();
        var statue = JsonParser.parseString(
                "{\"type\":\"minecraft:copper_golem_statue\",\"texture\":\"copper\",\"pose\":\"running\"}")
                .getAsJsonObject();

        assertEquals(List.of("chest"),
                MinecraftModelLoader.modelLayerCandidates(chest, "minecraft:block/chest"));
        assertEquals(List.of("copper_golem_statue", "copper_golem_statue_running"),
                MinecraftModelLoader.modelLayerCandidates(statue, "minecraft:block/copper_golem_statue"));
        assertEquals(List.of("bell_floor"),
                MinecraftModelLoader.modelLayerCandidates(null, "minecraft:block/bell_floor"));
    }
}
