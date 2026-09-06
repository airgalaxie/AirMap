package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.PatchDefinition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Contract test for the Minecraft chest special model. Checks the geometry data level:
 * every facing and type must produce a complete, six-sided chest that stays inside its block,
 * with the lock sitting flush on the facing's leading edge, and the two double-chest halves
 * together tiling one continuous 2-block-wide chest without a seam gap or overlap.
 */
class ChestGeometryContractTest {
    private static final double TOL = 1.0e-9;
    private static final String[] FACINGS = {"north", "south", "east", "west"};

    @Test
    void singleAndDoubleChestsFormCompleteSixSidedClosedGeometryInEveryFacing() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());

        DynmapBlockState[] singles = new DynmapBlockState[4];
        DynmapBlockState[] lefts = new DynmapBlockState[4];
        DynmapBlockState[] rights = new DynmapBlockState[4];
        DynmapBlockState stone = new DynmapBlockState.Builder().setBlockName("minecraft:stone")
                .setStateName("").setAttenuatesLight(15).build();

        DynmapBlockState base = new DynmapBlockState.Builder().setBlockName("minecraft:chest")
                .setStateName("facing=north,type=single,waterlogged=false").build();
        int idx = 1;
        for (int i = 0; i < 4; i++) {
            singles[i] = variant(base, idx++, "facing=" + FACINGS[i] + ",type=single,waterlogged=false");
        }
        for (int i = 0; i < 4; i++) {
            lefts[i] = variant(base, idx++, "facing=" + FACINGS[i] + ",type=left,waterlogged=false");
        }
        for (int i = 0; i < 4; i++) {
            rights[i] = variant(base, idx++, "facing=" + FACINGS[i] + ",type=right,waterlogged=false");
        }

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        assertEquals(6, patches(stone).length, "plain cube control block stays a six-sided box");

        for (int i = 0; i < 4; i++) {
            assertSingleChest(singles[i], FACINGS[i]);
        }
        for (int i = 0; i < 4; i++) {
            assertDoubleHalf(lefts[i], FACINGS[i], "left");
            assertDoubleHalf(rights[i], FACINGS[i], "right");
        }
        assertNorthDoubleTiles(lefts[0], rights[0]);
    }

    private static void assertSingleChest(DynmapBlockState state, String facing) {
        PatchDefinition[] p = patches(state);
        assertEquals(18, p.length, "single chest must be 3 boxes x 6 faces (" + facing + ")");
        assertAllSixNormals(p, "single " + facing);
        assertInBlock(p, "single " + facing);
        assertEquals(0.0, minY(p), TOL, "single " + facing + " base must rest on the block bottom");
        assertEquals(14.0 / 16.0, maxY(p), TOL, "single " + facing + " lid top must be at 14/16");
        switch (facing) {
            case "north": assertEquals(0.0, minZ(p), TOL, "north lock flush on north face"); break;
            case "south": assertEquals(1.0, maxZ(p), TOL, "south lock flush on south face"); break;
            case "east":  assertEquals(0.0, minX(p), TOL, "east lock flush on east face"); break;
            case "west":  assertEquals(1.0, maxX(p), TOL, "west lock flush on west face"); break;
            default: throw new AssertionError(facing);
        }
    }

    private static void assertDoubleHalf(DynmapBlockState state, String facing, String which) {
        PatchDefinition[] p = patches(state);
        assertEquals(15, p.length, "double-chest " + which + " (" + facing + ") must be a complete half");
        assertExteriorNormals(p, "double " + which + " " + facing);
        assertInBlock(p, "double " + which + " " + facing);
        assertEquals(0.0, minY(p), TOL, "double-chest " + which + " (" + facing + ") rests on block bottom");
    }

    private static void assertNorthDoubleTiles(DynmapBlockState left, DynmapBlockState right) {
        double leftMaxX = maxX(patches(left));
        double rightMinX = minX(patches(right));
        assertEquals(1.0, leftMaxX, TOL, "north left half must close the seam at its +X block edge");
        assertEquals(0.0, rightMinX, TOL, "north right half must close the seam at its -X block edge");
    }

    private static void assertAllSixNormals(PatchDefinition[] p, String label) {
        Set<BlockStep> normals = distinctNormals(p);
        assertEquals(6, normals.size(), label + " single chest must be six-sided");
        for (BlockStep s : BlockStep.values()) {
            assertTrue(normals.contains(s), label + " must have a face whose outward normal is " + s);
        }
    }

    private static void assertExteriorNormals(PatchDefinition[] p, String label) {
        Set<BlockStep> normals = distinctNormals(p);
        assertEquals(5, normals.size(), label + " double-chest half must omit exactly its interior seam face");
    }

    private static Set<BlockStep> distinctNormals(PatchDefinition[] p) {
        Set<BlockStep> normals = new HashSet<>();
        for (PatchDefinition pd : p) normals.add(pd.step);
        return normals;
    }

    private static void assertInBlock(PatchDefinition[] p, String label) {
        assertTrue(minX(p) >= -TOL && maxX(p) <= 1.0 + TOL, label + " X within block");
        assertTrue(minY(p) >= -TOL && maxY(p) <= 1.0 + TOL, label + " Y within block");
        assertTrue(minZ(p) >= -TOL && maxZ(p) <= 1.0 + TOL, label + " Z within block");
    }

    private static DynmapBlockState variant(DynmapBlockState base, int stateIndex, String properties) {
        return new DynmapBlockState.Builder().setBaseState(base).setStateIndex(stateIndex)
                .setBlockName(base.blockName).setStateName(properties).build();
    }

    private static PatchDefinition[] patches(DynmapBlockState state) {
        HDBlockModel model = HDBlockModels.models_by_id_data[state.globalStateIndex];
        assertNotNull(model, "missing model for " + state);
        assertTrue(model instanceof HDBlockPatchModel, "not a patch model for " + state);
        return ((HDBlockPatchModel) model).getPatches();
    }

    private static double ext(PatchDefinition[] p, int axis, int sign) {
        double best = sign < 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (PatchDefinition pd : p) for (double u : new double[] {pd.umin, pd.umax})
            for (double v : new double[] {pd.vmin, pd.vmax}) {
                double d = axis == 0 ? pd.x0 + pd.u.x * u + pd.v.x * v
                        : axis == 1 ? pd.y0 + pd.u.y * u + pd.v.y * v
                        : pd.z0 + pd.u.z * u + pd.v.z * v;
                best = sign < 0 ? Math.min(best, d) : Math.max(best, d);
            }
        return best;
    }

    private static double minX(PatchDefinition[] p) { return ext(p, 0, -1); }
    private static double maxX(PatchDefinition[] p) { return ext(p, 0, 1); }
    private static double minY(PatchDefinition[] p) { return ext(p, 1, -1); }
    private static double maxY(PatchDefinition[] p) { return ext(p, 1, 1); }
    private static double minZ(PatchDefinition[] p) { return ext(p, 2, -1); }
    private static double maxZ(PatchDefinition[] p) { return ext(p, 2, 1); }
}
