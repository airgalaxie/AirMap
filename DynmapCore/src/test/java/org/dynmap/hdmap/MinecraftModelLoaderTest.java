package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Test;

class MinecraftModelLoaderTest {
    @Test
    void preservesModelLayerUvsForBothPolygonWindings() throws Exception {
        var geometry = layer("chest");
        var loader = new MinecraftModelLoader(null, new PatchDefinitionFactory());

        var flipped = loader.modelLayerFace(
                geometry.getAsJsonObject("variants").getAsJsonObject("faces")
                        .getAsJsonArray("left").get(1).getAsJsonObject().getAsJsonArray("vertices"), 0);
        assertEquals(SideVisible.BOTTOM, flipped.sidevis);
        assertEquals(29.0 / 64.0, flipped.umin);
        assertEquals(44.0 / 64.0, flipped.umax);

        var normal = loader.modelLayerFace(
                geometry.getAsJsonObject("variants").getAsJsonObject("faces")
                        .getAsJsonArray("left").get(0).getAsJsonObject().getAsJsonArray("vertices"), 0);
        assertEquals(SideVisible.TOP, normal.sidevis);
        assertEquals(14.0 / 64.0, normal.umin);
        assertEquals(29.0 / 64.0, normal.umax);
    }

    @Test
    void rotatesChestLayerLikeMinecraftChestRenderer() {
        assertArrayEquals(new int[] {0, 0, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "south", null));
        assertArrayEquals(new int[] {0, 270, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "east", null));
        assertArrayEquals(new int[] {0, 180, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "north", null));
        assertArrayEquals(new int[] {0, 90, 0},
                MinecraftModelLoader.layerRotation("chest_facing", "west", null));
    }

    @Test
    void rotatesSouthBakedChestTowardEveryFacing() {
        assertFacing("south", 0.5, 1.0);
        assertFacing("east", 1.0, 0.5);
        assertFacing("north", 0.5, 0.0);
        assertFacing("west", 0.0, 0.5);
    }

    @Test
    void rotationPreservesPatchContractForEveryStaticLayer() throws Exception {
        var factory = new PatchDefinitionFactory();
        var loader = new MinecraftModelLoader(null, factory);
        for (String name : List.of("bell_between_walls", "bell_ceiling", "bell_floor", "bell_wall",
                "shulker_box", "copper_golem_statue_running", "copper_golem_statue_sitting",
                "copper_golem_statue_standing", "copper_golem_statue_star")) {
            var faces = layer(name).getAsJsonArray("faces");
            for (int i = 0; i < faces.size(); i++) {
                PatchDefinition patch = loader.modelLayerFace(
                        faces.get(i).getAsJsonObject().getAsJsonArray("vertices"), 0);
                PatchDefinition rotated = factory.getPatch(patch, 0, 90, 0, 0);
                String face = name + " face " + i;
                assertAll(face,
                        () -> assertEquals(patch.umin, rotated.umin),
                        () -> assertEquals(patch.umax, rotated.umax),
                        () -> assertEquals(patch.vmin, rotated.vmin),
                        () -> assertEquals(patch.vmax, rotated.vmax),
                        () -> assertEquals(patch.sidevis, rotated.sidevis));
            }
        }
    }

    private static void assertFacing(String facing, double expectedX, double expectedZ) {
        Vector3D front = new Vector3D(0.5, 0.5, 1.0);
        int angle = MinecraftModelLoader.layerRotation("chest_facing", facing, null)[1];
        PatchDefinition.rotateAround(front, 0, angle, 0);
        assertEquals(expectedX, front.x, 1.0E-12, facing);
        assertEquals(expectedZ, front.z, 1.0E-12, facing);
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
