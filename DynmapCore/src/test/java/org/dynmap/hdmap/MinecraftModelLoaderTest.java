package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinecraftModelLoaderTest {
    @Test
    void rotatesChestLayerLikeMinecraftChestRenderer() {
        assertArrayEquals(new int[] {0, 0, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "south", null));
        assertArrayEquals(new int[] {0, 90, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "east", null));
        assertArrayEquals(new int[] {0, 180, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "north", null));
        assertArrayEquals(new int[] {0, 270, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "west", null));
    }

    @Test
    void selectsDoubleChestLayerAndTextureFromMinecraftBlockStateType() throws Exception {
        var geometry = layer("chest");

        assertEquals(18, MinecraftModelLoader.modelLayerFaces(geometry, Map.of("type", "single")).size());
        assertEquals("minecraft:entity/chest/normal",
                MinecraftModelLoader.modelLayerTexture("minecraft:entity/chest/normal", geometry,
                        Map.of("type", "single")));
        assertEquals(15, MinecraftModelLoader.modelLayerFaces(geometry, Map.of("type", "left")).size());
        assertEquals("minecraft:entity/chest/normal_left",
                MinecraftModelLoader.modelLayerTexture("minecraft:entity/chest/normal", geometry,
                        Map.of("type", "left")));
        assertEquals(15, MinecraftModelLoader.modelLayerFaces(geometry, Map.of("type", "right")).size());
        assertEquals("minecraft:entity/chest/normal_right",
                MinecraftModelLoader.modelLayerTexture("minecraft:entity/chest/normal", geometry,
                        Map.of("type", "right")));
        assertEquals(18, geometry.getAsJsonArray("faces").size(), "single chest geometry changed");
    }

    private static com.google.gson.JsonObject layer(String name) throws Exception {
        try (var input = MinecraftModelLoaderTest.class.getResourceAsStream(
                "/minecraft-model-layers/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

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
