package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.junit.jupiter.api.Test;

/**
 * Locks the per-face uv-lock counter-rotation table against the authoritative vanilla result
 * (BlockModelRotation.inverseFaceTransformation, executed against the
 * 26.4-snapshot-3 client jar). The baked per-vertex atlas uv is
 * cornerToCenter -> matrix -> centerToCorner
 * on the JSON uv, so the vanilla ops live directly in atlas (u right, v down) space. Vanilla only
 * ever emits id/ROT180/ROT90CCW/ROT90CW for (x,y,z) in {0,90,180,270}3, mapped here onto the AirMap
 * face rotations DEG180/DEG90(=1-v,u)/DEG270(=v,1-u).
 */
class MinecraftModelUvLockTableTest {
    private void uv(int expX, int expY, int expZ, int face, int want) {
        assertEquals(rotation(want), MinecraftModelLoader.uvLockedRotation(
                        ModelBlockModel.SideRotation.DEG0, expX, expY, expZ, faceOf(face)),
                "x" + expX + "y" + expY + "z" + expZ + " f" + face);
    }

    private static ModelBlockModel.SideRotation rotation(int turns) {
        return ModelBlockModel.SideRotation.values()[turns];
    }

    private static BlockSide faceOf(int face) {
        switch (face) {
            case 0: return BlockSide.BOTTOM;
            case 1: return BlockSide.TOP;
            case 2: return BlockSide.NORTH;
            case 3: return BlockSide.SOUTH;
            case 4: return BlockSide.WEST;
            default: return BlockSide.EAST;
        }
    }

    /* Full authoritative 64x6 table generated against the vanilla 26.4 snapshot 3 jar. */
    private static final int[][] EXPECTED = {
        { 0, 0, 0, 0, 0, 0 },
        {1,1,3,1,1,1}, {2,2,2,2,2,2}, {3,3,1,3,3,3},
        {3,1,0,0,0,0}, {0,2,1,1,3,1}, {1,3,2,2,2,2}, {2,0,3,3,1,3},
        {2,2,0,0,0,0}, {3,3,1,3,1,1}, {0,0,2,2,2,2}, {1,1,3,1,3,3},
        {1,3,0,0,0,0}, {2,0,1,1,1,3}, {3,1,2,2,2,2}, {0,2,3,3,3,1},
        {0,2,2,0,3,1}, {1,1,3,1,0,2}, {2,0,0,2,1,3}, {3,3,1,3,2,0},
        {0,2,1,1,3,1}, {1,3,2,2,2,2}, {2,0,3,3,1,3}, {3,1,0,0,0,0},
        {0,2,0,2,3,1}, {3,3,1,3,0,2}, {2,0,2,0,1,3}, {1,1,3,1,2,0},
        {0,2,3,3,3,1}, {1,3,0,0,0,0}, {2,0,1,1,1,3}, {3,1,2,2,2,2},
        {0,0,2,2,2,2}, {1,1,3,1,3,3}, {2,2,0,0,0,0}, {3,3,1,3,1,1},
        {1,3,2,2,2,2}, {2,0,3,3,1,3}, {3,1,0,0,0,0}, {0,2,1,1,3,1},
        {2,2,2,2,2,2}, {3,3,1,3,3,3}, {0,0,0,0,0,0}, {1,1,3,1,1,1},
        {3,1,2,2,2,2}, {0,2,3,3,3,1}, {1,3,0,0,0,0}, {2,0,1,1,1,3},
        {2,0,2,0,1,3}, {1,1,3,1,2,0}, {0,2,0,2,3,1}, {3,3,1,3,0,2},
        {2,0,3,3,1,3}, {3,1,0,0,0,0}, {0,2,1,1,3,1}, {1,3,2,2,2,2},
        {2,0,0,2,1,3}, {3,3,1,3,2,0}, {0,2,2,0,3,1}, {1,1,3,1,0,2},
        {2,0,1,1,1,3}, {3,1,2,2,2,2}, {0,2,3,3,3,1}, {1,3,0,0,0,0} };

    @Test
    void fullVanillaTableMatchesAllThreeHundredEightyFourFaces() {
        int[] angles = { 0, 90, 180, 270 };
        for (int xi = 0; xi < 4; xi++) {
            for (int yi = 0; yi < 4; yi++) {
                for (int zi = 0; zi < 4; zi++) {
                    for (int fi = 0; fi < 6; fi++) {
                        uv(angles[xi], angles[yi], angles[zi], fi,
                                EXPECTED[xi * 16 + yi * 4 + zi][fi]);
                    }
                }
            }
        }
    }

    @Test
    void identityFaceLeavesFaceRotationUntouched() {
        assertEquals(ModelBlockModel.SideRotation.DEG90, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, 0, BlockSide.NORTH));
    }

    @Test
    void jsonFaceRotationComposesWithUvLock() {
        assertEquals(ModelBlockModel.SideRotation.DEG180, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, 0, BlockSide.TOP));
        assertEquals(ModelBlockModel.SideRotation.DEG0, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, 0, BlockSide.BOTTOM));
    }
}
