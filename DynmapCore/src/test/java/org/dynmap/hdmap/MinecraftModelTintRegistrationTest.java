package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Proves biome tint modifiers survive registration into the per-state texture maps. */
class MinecraftModelTintRegistrationTest {
    private static final int TINT_MULT = 1_000_000;
    @Test
    void grassTopsAndLeavesCarryBiomeTintModifiers() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());

        DynmapBlockState grassDefault = new DynmapBlockState.Builder().setBlockName("minecraft:grass_block")
                .setStateName("snowy=false").setAttenuatesLight(15).build();
        DynmapBlockState grassSnowy = new DynmapBlockState.Builder().setBaseState(grassDefault).setStateIndex(1)
                .setBlockName("minecraft:grass_block").setStateName("snowy=true").setAttenuatesLight(15).build();
        DynmapBlockState leaves = new DynmapBlockState.Builder().setBlockName("minecraft:oak_leaves").setLeaves().build();
        DynmapBlockState plant = new DynmapBlockState.Builder().setBlockName("minecraft:short_grass")
                .setStateName("waterlogged=false").build();
        DynmapBlockState stone = new DynmapBlockState.Builder().setBlockName("minecraft:stone")
                .setAttenuatesLight(15).build();
        DynmapBlockState heavyCore = new DynmapBlockState.Builder().setBlockName("minecraft:heavy_core")
                .setAttenuatesLight(15).build();
        DynmapBlockState water = new DynmapBlockState.Builder().setBlockName("minecraft:water")
                .setStateName("level=0").build();
        DynmapBlockState flowingWater = new DynmapBlockState.Builder().setBlockName("minecraft:flowing_water")
                .setStateName("level=1").build();
        DynmapBlockState lava = new DynmapBlockState.Builder().setBlockName("minecraft:lava")
                .setStateName("level=0").build();

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        assertSomeFacesCarry(grassDefault, TexturePack.COLORMOD_GRASSTONED, "untinted grass_block top face");
        assertTrue(HDBlockStateTextureMap.getByBlockState(grassSnowy) == null
                || !carries(HDBlockStateTextureMap.getByBlockState(grassSnowy), TexturePack.COLORMOD_GRASSTONED),
                "snowy grass must not use grass colormap");
        assertAllFacesCarry(leaves, TexturePack.COLORMOD_FOLIAGETONED, "untinted oak_leaves face");
        assertSomeFacesCarry(plant, TexturePack.COLORMOD_GRASSTONED, "untinted short_grass face");
        HDBlockModel plantModel = HDBlockModels.models_by_id_data[plant.globalStateIndex];
        assertTrue(plantModel instanceof HDBlockPatchModel, "short_grass must use its Minecraft patch model");
        for (var patch : ((HDBlockPatchModel) plantModel).getPatches()) {
            assertEquals(BlockStep.Y_MINUS, patch.shadeStep,
                    "cross plants must preserve Minecraft's upward shade_direction_override");
        }
        assertEquals(TexturePack.BlockTransparency.OPAQUE,
                HDBlockStateTextureMap.getByBlockState(grassDefault).trans,
                "solid grass_block must stay opaque despite multi-element model");
        assertEquals(TexturePack.BlockTransparency.OPAQUE,
                HDBlockStateTextureMap.getByBlockState(stone).trans,
                "stone must be opaque");
        assertEquals(TexturePack.BlockTransparency.SEMITRANSPARENT,
                HDBlockStateTextureMap.getByBlockState(heavyCore).trans,
                "opaque sprites must not make partial-block geometry opaque");

        HDBlockStateTextureMap waterMap = HDBlockStateTextureMap.getByBlockState(water);
        assertNotNull(waterMap, "water must get an explicit fluid model");
        assertNotNull(waterMap.faces, "water model must have textures");
        assertEquals(TexturePack.BlockTransparency.SEMITRANSPARENT, waterMap.trans, "water must stay semitransparent");
        int waterTile = waterMap.faces[0];
        // CLEARINSIDE op: readColor culls internal water-water faces and falls through to
        // COLORMOD_WATERTONED for surviving faces - without it water stacks up fully opaque.
        assertEquals(TexturePack.COLORMOD_CLEARINSIDE, waterTile / TexturePack.COLORMOD_MULT_INTERNAL,
                "water_still.png is grayscale in modern MC; CLEARINSIDE gives biome tone + face culling");
        assertTrue(waterTile % TexturePack.COLORMOD_MULT_INTERNAL > 267,
                "water must resolve to a real dynamic tile, got " + waterTile);
        assertEquals(2, waterMap.faces.length, "fluid model needs Minecraft's still and flowing textures");
        assertEquals(TexturePack.COLORMOD_CLEARINSIDE,
                waterMap.faces[1] / TexturePack.COLORMOD_MULT_INTERNAL,
                "water_flow.png needs the same tint and internal-face operation");
        assertTrue(HDBlockModels.models_by_id_data[water.globalStateIndex] instanceof CustomBlockModel,
                "water states must use the Minecraft-compatible fluid surface renderer");

        HDBlockStateTextureMap flowingMap = HDBlockStateTextureMap.getByBlockState(flowingWater);
        assertNotNull(flowingMap, "flowing_water must get an explicit fluid model");
        assertEquals(TexturePack.BlockTransparency.SEMITRANSPARENT, flowingMap.trans,
                "flowing_water must stay semitransparent");
        assertEquals(TexturePack.COLORMOD_CLEARINSIDE, flowingMap.faces[0] / TexturePack.COLORMOD_MULT_INTERNAL,
                "flowing_water needs the CLEARINSIDE cull too");

        HDBlockStateTextureMap lavaMap = HDBlockStateTextureMap.getByBlockState(lava);
        assertNotNull(lavaMap, "lava must get an explicit fluid cube");
        assertEquals(TexturePack.BlockTransparency.OPAQUE, lavaMap.trans, "lava must render opaque");
    }

    private static void assertSomeFacesCarry(DynmapBlockState state, int expectedModifier, String message) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(state);
        assertNotNull(map, "no texture map registered for " + state.toString());
        assertNotNull(map.faces, "no faces registered for " + state.toString());
        assertTrue(carries(map, expectedModifier),
                message + " (" + state.toString() + ", raw faces=" + Arrays.toString(map.faces) + ")");
    }

    private static void assertAllFacesCarry(DynmapBlockState state, int expectedModifier, String message) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(state);
        assertNotNull(map, "no texture map registered for " + state.toString());
        assertNotNull(map.faces, "no faces registered for " + state.toString());
        assertTrue(map.faces.length > 0, "empty face list for " + state.toString());
        for (int i = 0; i < map.faces.length; i++) {
            assertEquals(expectedModifier, map.faces[i] / TINT_MULT,
                    message + " (face " + i + " of " + state.toString() + ", raw=" + map.faces[i] + ")");
        }
    }

    private static boolean carries(HDBlockStateTextureMap map, int modifier) {
        return map != null && map.faces != null
                && Arrays.stream(map.faces).anyMatch(f -> f / TINT_MULT == modifier);
    }
}
