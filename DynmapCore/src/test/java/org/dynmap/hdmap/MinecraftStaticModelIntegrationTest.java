package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        DynmapBlockState chest = state("minecraft:chest", "facing=north,type=single,waterlogged=false", false);
        DynmapBlockState chestLeft = variant(chest, 1,
                "facing=north,type=left,waterlogged=false");
        DynmapBlockState chestRight = variant(chest, 2,
                "facing=north,type=right,waterlogged=false");
        DynmapBlockState copperChest = state("minecraft:copper_chest", "facing=north,type=single,waterlogged=false", false);
        DynmapBlockState shulkerBox = state("minecraft:shulker_box", "facing=up", false);
        DynmapBlockState bell = state("minecraft:bell",
                "attachment=floor,facing=east,powered=false", false);
        DynmapBlockState statue = state("minecraft:copper_golem_statue",
                "copper_golem_pose=standing,facing=north", false);
        DynmapBlockState redMushroomUp = mushroom("minecraft:red_mushroom_block", true);
        DynmapBlockState redMushroomDown = mushroomVariant(redMushroomUp, false);
        DynmapBlockState brownMushroomUp = mushroom("minecraft:brown_mushroom_block", true);
        DynmapBlockState brownMushroomDown = mushroomVariant(brownMushroomUp, false);
        DynmapBlockState torch = state("minecraft:torch", "", false);
        DynmapBlockState wallTorchEast = state("minecraft:wall_torch", "facing=east", false);

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        assertEquals(6, patches(stone).length, "full cube control model");
        assertEquals(6, patches(heavyCore).length, "partial heavy-core model");
        assertTrue(patches(stairsEast).length > 6, "oriented stair model");
        assertTrue(patches(stairsNorth).length > 6, "rotated stair model");
        assertTrue(patches(fence).length > 6, "multipart fence model");
        assertEquals(18, patches(chest).length, "Minecraft chest model layer");
        assertEquals(-1.0 / 16.0, minZ(patches(chest)), 1.0e-9,
                "north-facing chest lock must point north");
        assertEquals(15.0 / 16.0, maxZ(patches(chest)), 1.0e-9,
                "north-facing chest body must retain its vanilla bounds");
        assertEquals(15, patches(chestLeft).length, "Minecraft double-chest left layer");
        assertEquals(15, patches(chestRight).length, "Minecraft double-chest right layer");
        assertEquals(1.0 / 16.0, minX(patches(chestLeft)), 1.0e-9,
                "north-facing left half must close the positive-X seam");
        assertEquals(1.0, maxX(patches(chestLeft)), 1.0e-9,
                "north-facing left half must reach its positive-X block edge");
        assertEquals(0.0, minX(patches(chestRight)), 1.0e-9,
                "north-facing right half must reach its negative-X block edge");
        assertEquals(15.0 / 16.0, maxX(patches(chestRight)), 1.0e-9,
                "north-facing right half must close the negative-X seam");
        assertTrue(firstTexture(chest) != firstTexture(chestLeft));
        assertTrue(firstTexture(chest) != firstTexture(chestRight));
        assertTrue(firstTexture(chestLeft) != firstTexture(chestRight));
        assertEquals(18, patches(copperChest).length, "Minecraft copper-chest model layer");
        assertEquals(12, patches(shulkerBox).length, "Minecraft shulker-box model layer");
        assertEquals(0.00025, minX(patches(shulkerBox)), 1.0e-9, "special-model translation and scale");
        assertEquals(0.99975, maxX(patches(shulkerBox)), 1.0e-9, "special-model translation and scale");
        assertEquals(28, patches(bell).length, "static bell support plus Minecraft bell model layer");
        assertEquals(54, patches(statue).length, "Minecraft copper-golem standing model layer");
        assertMushroomVerticalFaces(redMushroomUp, redMushroomDown);
        assertMushroomVerticalFaces(brownMushroomUp, brownMushroomDown);
        assertEquals(0.0, minY(patches(torch)), 1.0e-9, "standing torch base");
        assertEquals(10.0 / 16.0, maxY(patches(torch)), 1.0e-9, "standing torch height");
        assertTrue(maxX(patches(wallTorchEast)) > 0.25,
                "east wall torch must lean away from its west wall into +X");
    }

    private static DynmapBlockState state(String name, String properties, boolean opaque) {
        var builder = new DynmapBlockState.Builder().setBlockName(name).setStateName(properties);
        if (opaque) builder.setAttenuatesLight(15);
        return builder.build();
    }

    private static DynmapBlockState variant(DynmapBlockState base, int stateIndex, String properties) {
        return new DynmapBlockState.Builder().setBaseState(base).setStateIndex(stateIndex)
                .setBlockName(base.blockName).setStateName(properties).build();
    }

    private static int firstTexture(DynmapBlockState state) {
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(state);
        assertNotNull(map, "missing texture map for " + state);
        assertNotNull(map.faces, "missing textures for " + state);
        return map.faces[0];
    }

    private static DynmapBlockState mushroom(String name, boolean up) {
        return state(name, mushroomProperties(up), false);
    }

    private static DynmapBlockState mushroomVariant(DynmapBlockState base, boolean up) {
        return new DynmapBlockState.Builder().setBaseState(base).setStateIndex(1)
                .setBlockName(base.blockName).setStateName(mushroomProperties(up)).build();
    }

    private static String mushroomProperties(boolean up) {
        return "down=" + !up + ",east=false,north=false,south=false,up=" + up + ",west=false";
    }

    private static void assertMushroomVerticalFaces(DynmapBlockState up, DynmapBlockState down) {
        var upPatches = patches(up);
        var downPatches = patches(down);
        assertEquals(0, textureAtY(upPatches, 1.0), "up=true cap must be on top");
        assertEquals(5, textureAtY(upPatches, 0.0), "down=false inside must be on bottom");
        assertEquals(5, textureAtY(downPatches, 1.0), "up=false inside must be on top");
        assertEquals(0, textureAtY(downPatches, 0.0), "down=true cap must be on bottom");
    }

    private static int textureAtY(org.dynmap.utils.PatchDefinition[] patches, double y) {
        return java.util.Arrays.stream(patches)
                .filter(p -> Math.abs(p.y0 - y) < 1.0e-9
                        && Math.abs(p.yu - y) < 1.0e-9 && Math.abs(p.yv - y) < 1.0e-9)
                .mapToInt(p -> p.textureindex).findFirst().orElseThrow();
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

    private static double minX(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.x0 + p.u.x * p.umin + p.v.x * p.vmin,
                p.x0 + p.u.x * p.umax + p.v.x * p.vmin,
                p.x0 + p.u.x * p.umin + p.v.x * p.vmax,
                p.x0 + p.u.x * p.umax + p.v.x * p.vmax)).min().orElseThrow();
    }

    private static double maxX(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.x0 + p.u.x * p.umin + p.v.x * p.vmin,
                p.x0 + p.u.x * p.umax + p.v.x * p.vmin,
                p.x0 + p.u.x * p.umin + p.v.x * p.vmax,
                p.x0 + p.u.x * p.umax + p.v.x * p.vmax)).max().orElseThrow();
    }

    private static double minY(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.y0 + p.u.y * p.umin + p.v.y * p.vmin,
                p.y0 + p.u.y * p.umax + p.v.y * p.vmin,
                p.y0 + p.u.y * p.umin + p.v.y * p.vmax,
                p.y0 + p.u.y * p.umax + p.v.y * p.vmax)).min().orElseThrow();
    }

    private static double maxY(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.y0 + p.u.y * p.umin + p.v.y * p.vmin,
                p.y0 + p.u.y * p.umax + p.v.y * p.vmin,
                p.y0 + p.u.y * p.umin + p.v.y * p.vmax,
                p.y0 + p.u.y * p.umax + p.v.y * p.vmax)).max().orElseThrow();
    }

    private static double minZ(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.z0 + p.u.z * p.umin + p.v.z * p.vmin,
                p.z0 + p.u.z * p.umax + p.v.z * p.vmin,
                p.z0 + p.u.z * p.umin + p.v.z * p.vmax,
                p.z0 + p.u.z * p.umax + p.v.z * p.vmax)).min().orElseThrow();
    }

    private static double maxZ(org.dynmap.utils.PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).flatMapToDouble(p -> java.util.stream.DoubleStream.of(
                p.z0 + p.u.z * p.umin + p.v.z * p.vmin,
                p.z0 + p.u.z * p.umax + p.v.z * p.vmin,
                p.z0 + p.u.z * p.umin + p.v.z * p.vmax,
                p.z0 + p.u.z * p.umax + p.v.z * p.vmax)).max().orElseThrow();
    }
}
