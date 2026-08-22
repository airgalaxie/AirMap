package org.dynmap.utils;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.dynmap.modsupport.BlockSide;
import org.junit.jupiter.api.Test;

class PatchDefinitionShadeStepTest {
    private static final double[] FROM = { 0, 0, 0 };
    private static final double[] TO = { 16, 16, 16 };

    @Test
    void keepsLegacyPatchesWithoutAnOverride() {
        PatchDefinitionFactory factory = new PatchDefinitionFactory();
        PatchDefinition patch = factory.getModelFace(FROM, TO, BlockSide.NORTH, null, null, true, 0);

        assertNull(patch.shadeStep);
    }

    @Test
    void cachesShadeDirectionAsPartOfPatchIdentity() {
        PatchDefinitionFactory factory = new PatchDefinitionFactory();
        PatchDefinition up = factory.getModelFace(FROM, TO, BlockSide.NORTH, null, null,
                true, BlockStep.Y_MINUS, 0);
        PatchDefinition sameUp = factory.getModelFace(FROM, TO, BlockSide.NORTH, null, null,
                true, BlockStep.Y_MINUS, 0);
        PatchDefinition north = factory.getModelFace(FROM, TO, BlockSide.NORTH, null, null,
                true, BlockStep.Z_PLUS, 0);

        assertSame(up, sameUp);
        assertNotEquals(up, north);
    }

    @Test
    void rotatesShadeDirectionWithTheModel() {
        PatchDefinitionFactory factory = new PatchDefinitionFactory();
        PatchDefinition north = factory.getModelFace(FROM, TO, BlockSide.NORTH, null, null,
                true, BlockStep.Z_PLUS, 0);

        PatchDefinition rotated = factory.getPatch(north, 0, 90, 0, 0);

        assertSame(BlockStep.X_MINUS, rotated.shadeStep);
    }
}
