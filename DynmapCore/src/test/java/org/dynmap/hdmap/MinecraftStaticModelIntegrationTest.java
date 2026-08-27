package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Verifies representative static block models directly against the configured Minecraft client resources. */
class MinecraftStaticModelIntegrationTest {
    @Test
    void loadsFullPartialOrientedAndMultipartModelsAndKeepsResourceBoundary() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());

        DynmapBlockState stone = state("minecraft:stone", "", true);
        DynmapBlockState heavyCore = state("minecraft:heavy_core", "", false);
        DynmapBlockState stairsEast = state("minecraft:oak_stairs",
                "facing=east,half=bottom,shape=straight,waterlogged=false", false);
        DynmapBlockState stairsNorth = new DynmapBlockState.Builder().setBaseState(stairsEast).setStateIndex(1)
                .setBlockName("minecraft:oak_stairs")
                .setStateName("facing=north,half=bottom,shape=straight,waterlogged=false").build();
        DynmapBlockState fence = state("minecraft:oak_fence",
                "east=true,north=false,south=true,waterlogged=false,west=false", false);
        DynmapBlockState bell = state("minecraft:bell",
                "attachment=floor,facing=east,powered=false", false);
        DynmapBlockState statue = state("minecraft:copper_golem_statue", "", false);

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        assertEquals(6, patches(stone).length, "full cube control model");
        assertEquals(6, patches(heavyCore).length, "partial heavy-core model");
        assertTrue(patches(stairsEast).length > 6, "oriented stair model");
        assertTrue(patches(stairsNorth).length > 6, "rotated stair model");
        assertTrue(patches(fence).length > 6, "multipart fence model");
        assertEquals(16, patches(bell).length,
                "Minecraft's static bell resource describes only the floor support");
        assertNull(model(statue),
                "particle-only Minecraft resource must not be replaced with invented static geometry");
    }

    private static DynmapBlockState state(String name, String properties, boolean opaque) {
        var builder = new DynmapBlockState.Builder().setBlockName(name).setStateName(properties);
        if (opaque) builder.setAttenuatesLight(15);
        return builder.build();
    }

    private static HDBlockModel model(DynmapBlockState state) {
        return HDBlockModels.models_by_id_data[state.globalStateIndex];
    }

    private static org.dynmap.utils.PatchDefinition[] patches(DynmapBlockState state) {
        HDBlockModel model = model(state);
        assertNotNull(model, "missing model for " + state);
        assertTrue(model instanceof HDBlockPatchModel, "not a patch model for " + state);
        return ((HDBlockPatchModel) model).getPatches();
    }
}
