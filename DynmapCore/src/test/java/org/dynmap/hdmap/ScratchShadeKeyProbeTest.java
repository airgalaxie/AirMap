package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Test;

/**
 * Scratch probe: records which shade key the JSON patch path feeds to
 * TexturePackHDShader for the 26.2 cross (shade=false), the 26.3 cross
 * (shade_direction_override="up"), and a plain cube, and contrasts the
 * resulting multipliers against vanilla's FaceBakery constants.
 * Unversioned evidence, not part of the test suite.
 */
class ScratchShadeKeyProbeTest {
    private static final Vector3D CENTER = new Vector3D(0.5, 0.5, 0.5);

    private static final String[] MULTIPLIER = {
        "key step: X (62.7% / 60% act/tbl), Y_MINUS (85.1/90.2% even/odd | 95.3/100% tbl), Y_PLUS (85.1/90.2% | 50%), Z (100% | 80.4%), "
    };

    private PatchDefinition crossBlade(BlockSide side, boolean shade, BlockStep override) {
        PatchDefinitionFactory patches = new PatchDefinitionFactory();
        PatchDefinition pd = patches.getModelFace(
                new double[] { 0.8, 0, 8 }, new double[] { 15.2, 16, 8 }, side,
                new double[] { 0, 0, 16, 16 }, ModelBlockModel.SideRotation.DEG0,
                shade, override, 0);
        assertNotNull(pd);
        double[] scale = { 1.4142135623730951, 1, 1.4142135623730951 };
        pd = patches.getScaledPatch(pd, scale[0], scale[1], scale[2], CENTER, pd.textureindex);
        if (pd == null) return null;
        return patches.getPatch(pd, 0, 45, 0, CENTER, pd.textureindex);
    }

    private PatchDefinition cubeFace(BlockSide side) {
        PatchDefinitionFactory patches = new PatchDefinitionFactory();
        return patches.getModelFace(
                new double[] { 0, 0, 0 }, new double[] { 16, 16, 16 }, side,
                null, ModelBlockModel.SideRotation.DEG0, true, null, 0);
    }

    private static BlockStep keyStep(BlockStep step, BlockStep shadeStep) {
        return shadeStep != null ? shadeStep : (step != null ? step.opposite() : null);
    }

    private static String key(BlockStep step, BlockStep shadeStep) {
        BlockStep k = keyStep(step, shadeStep);
        return k == null ? "null" : k.name();
    }

    private static String vanillaFactor(BlockStep key) {
        switch (key) {
            case Y_MINUS: return "1.0 (up)";
            case Y_PLUS: return "0.5 (down)";
            case Z_PLUS: case Z_MINUS: return "0.8 (north/south)";
            case X_PLUS: case X_MINUS: return "0.6 (east/west)";
            default: return "n/a";
        }
    }

    private static String shadeMult(BlockStep key, boolean table) {
        if (key == null) return "none (shade=false)";
        switch (key) {
            case X_PLUS: case X_MINUS:
                return table ? "60%" : "62.7%";
            case Y_MINUS:
                return table ? "95.3% (even) / 100% (odd)" : "85.1% (even) / 90.2% (odd)";
            case Y_PLUS:
                return table ? "50.2%" : "85.1% (even) / 90.2% (odd)";
            default:
                return table ? "80.4%" : "100% (no darken)";
        }
    }

    @Test
    void probeCrossAndCubeShadeKeys() {
        System.out.println("=== shader step -> multiplier (TexturePackHDShader.processBlock) ===");
        System.out.println("  table : X=0x99 60% | Y_MINUS=0xF3(even)/none(odd) | Y_PLUS=0x80 50% | Z=0xCD 80%");
        System.out.println("  active: X=0xA0 62.7% | Y_MINUS/Y_PLUS=0xD9(even)/0xE6(odd) | Z=none 100%");
        System.out.println();

        System.out.println("=== plain cube: all six faces (shade=true, no override) ===");
        for (BlockSide side : new BlockSide[] { BlockSide.TOP, BlockSide.BOTTOM,
                BlockSide.NORTH, BlockSide.SOUTH, BlockSide.WEST, BlockSide.EAST }) {
            PatchDefinition pd = cubeFace(side);
            assertNotNull(pd);
            BlockStep ks = keyStep(pd.step, pd.shadeStep);
            String k = ks == null ? "null" : ks.name();
            System.out.printf("  %-7s step=%-8s shade=%-5s shadeStep=%-8s key=%s  vanilla=%s  active=%s%n",
                    side, pd.step, pd.shade, pd.shadeStep, k, vanillaFactor(ks),
                    shadeMult(keyStep(pd.step, null), false));
        }
        System.out.println();

        System.out.println("=== 26.2 cross (shade=false) — blade elements, 45deg/rescale ===");
        for (BlockSide side : new BlockSide[] { BlockSide.NORTH, BlockSide.SOUTH, BlockSide.WEST, BlockSide.EAST }) {
            PatchDefinition pd = crossBlade(side, false, null);
            if (pd == null) continue;
            System.out.printf("  %-7s step=%-8s shade=%-5s shadeStep=%-8s key=%s  active=%s%n",
                    side, pd.step, pd.shade, pd.shadeStep, key(pd.step, pd.shadeStep),
                    shadeMult(keyStep(pd.step, pd.shadeStep), false));
            assertFalse(pd.shade);
        }
        System.out.println();

        System.out.println("=== 26.3 cross (shade_direction_override=\"up\") — blade elements ===");
        for (BlockSide side : new BlockSide[] { BlockSide.NORTH, BlockSide.SOUTH, BlockSide.WEST, BlockSide.EAST }) {
            PatchDefinition pd = crossBlade(side, true, BlockStep.Y_MINUS);
            if (pd == null) continue;
            System.out.printf("  %-7s step=%-8s shade=%-5s shadeStep=%-8s key=%s  vanilla=%s  active=%s  table=%s%n",
                    side, pd.step, pd.shade, pd.shadeStep, key(pd.step, pd.shadeStep),
                    vanillaFactor(pd.shadeStep != null ? pd.shadeStep : keyStep(pd.step, null)), shadeMult(keyStep(pd.step, pd.shadeStep), false),
                    shadeMult(keyStep(pd.step, pd.shadeStep), true));
            assertTrue(pd.shade);
            assertEquals(BlockStep.Y_MINUS, pd.shadeStep, "override \"up\" must survive rescale+rotation");
            assertEquals("Y_MINUS", key(pd.step, pd.shadeStep));
        }
        System.out.println();

        System.out.println("SUMMARY deviation: 26.3 override \"up\" -> key Y_MINUS -> active 85.1/90.2%% by getY-parity");
        System.out.println("(two blades of one cross), vanilla constant up=1.0. 26.2 shade=false -> 100%.");
    }
}