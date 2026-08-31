package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.junit.jupiter.api.Test;

/**
 * Locks the per-face uv-lock counter-rotation table against the authoritative vanilla result
 * (FaceBakery.bakeVertex + BlockModelRotation.inverseFaceTransformation, executed against the
 * 26.2 and 26.3-snapshot-10 client jars). The baked per-vertex atlas uv is
 * cornerToCenter -> matrix -> centerToCorner
 * on the JSON uv, so the vanilla ops live directly in atlas (u right, v down) space. Vanilla only
 * ever emits id/ROT180/ROT90CCW/ROT90CW for (x,y) in {0,90,180,270}2, mapped here onto the AirMap
 * face rotations DEG180/DEG90(=1-v,u)/DEG270(=v,1-u).
 */
class MinecraftModelUvLockTableTest {
    private void uv(int expX, int expY, int face, int want) {
        assertEquals(rotation(want), MinecraftModelLoader.uvLockedRotation(
                        ModelBlockModel.SideRotation.DEG0, expX, expY, faceOf(face)),
                "x" + expX + "y" + expY + " f" + face);
    }

    private static ModelBlockModel.SideRotation rotation(int modifier) {
        if (modifier == TexturePack.COLORMOD_ROT90) return ModelBlockModel.SideRotation.DEG90;
        if (modifier == TexturePack.COLORMOD_ROT180) return ModelBlockModel.SideRotation.DEG180;
        if (modifier == TexturePack.COLORMOD_ROT270) return ModelBlockModel.SideRotation.DEG270;
        return ModelBlockModel.SideRotation.DEG0;
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

    /* Full authoritative 16x6 table generated against the vanilla jar (GenTable). */
    private static final int[][] EXPECTED = {
        { 0, 0, 0, 0, 0, 0 },
        { TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90, 0, 0, 0, 0 },
        { TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, 0, 0, 0, 0 },
        { TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270, 0, 0, 0, 0 },
        { 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90 },
        { 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90 },
        { 0, TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90 },
        { 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90 },
        { 0, 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180 },
        { TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180 },
        { TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180 },
        { TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT180 },
        { TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270 },
        { TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT270, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270 },
        { TexturePack.COLORMOD_ROT180, 0, 0, TexturePack.COLORMOD_ROT180, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270 },
        { TexturePack.COLORMOD_ROT180, 0, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT90, TexturePack.COLORMOD_ROT270 } };

    @Test
    void fullVanillaTableMatchesAllNinetySixFaces() {
        int[] angles = { 0, 90, 180, 270 };
        for (int xi = 0; xi < 4; xi++) {
            for (int yi = 0; yi < 4; yi++) {
                for (int fi = 0; fi < 6; fi++) {
                    uv(angles[xi], angles[yi], fi, EXPECTED[xi * 4 + yi][fi]);
                }
            }
        }
    }

    @Test
    void identityFaceLeavesFaceRotationUntouched() {
        assertEquals(ModelBlockModel.SideRotation.DEG90, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, BlockSide.NORTH));
    }

    @Test
    void jsonFaceRotationComposesWithUvLock() {
        assertEquals(ModelBlockModel.SideRotation.DEG180, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, BlockSide.TOP));
        assertEquals(ModelBlockModel.SideRotation.DEG0, MinecraftModelLoader.uvLockedRotation(
                ModelBlockModel.SideRotation.DEG90, 0, 90, BlockSide.BOTTOM));
    }
}
