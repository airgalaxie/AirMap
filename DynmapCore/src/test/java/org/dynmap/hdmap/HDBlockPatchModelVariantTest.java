package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.junit.jupiter.api.Test;

class HDBlockPatchModelVariantTest {
    @Test
    void usesMinecraft263PositionSeedAndLegacyRandomSource() {
        assertEquals(0L, HDBlockPatchModel.positionSeed(0, 0, 0));
        assertEquals(-33674130277896L, HDBlockPatchModel.positionSeed(1, 2, 3));
        assertEquals(134845739924965L, HDBlockPatchModel.positionSeed(10, 64, -7));
    }

    @Test
    void selectsEachMultipartWeightedListFromOnePositionSeededRandomStream() {
        DynmapBlockState state = new DynmapBlockState.Builder()
                .setBlockName("test:weighted_variants_" + System.nanoTime()).build();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];

        var first = new HDBlockPatchModel.WeightedPatchGroup(
                List.of(patches(0), patches(1)), List.of(1, 3));
        var second = new HDBlockPatchModel.WeightedPatchGroup(
                List.of(patches(2), patches(3)), List.of(2, 3));
        var model = new HDBlockPatchModel(state, List.of(first, second), "test", false);

        assertArrayEquals(new int[] {1, 3}, textureIndices(model.getPatches(0, 0, 0)),
                "seed 0 yields draws 2/4 then 3/5");
        assertArrayEquals(new int[] {0, 3}, textureIndices(model.getPatches(1, 0, 0)),
                "the second multipart draw must continue the same random stream");
        assertArrayEquals(textureIndices(model.getPatches(1, 2, 3)),
                textureIndices(model.getPatches(1, 2, 3)), "selection must be stable per position");
    }

    private static PatchDefinition[] patches(int textureIndex) {
        PatchDefinition patch = new PatchDefinitionFactory().getModelFace(
                new double[] {0, 0, 0}, new double[] {16, 16, 16}, BlockSide.TOP, null,
                ModelBlockModel.SideRotation.DEG0, true, textureIndex);
        return new PatchDefinition[] {patch};
    }

    private static int[] textureIndices(PatchDefinition[] patches) {
        return java.util.Arrays.stream(patches).mapToInt(PatchDefinition::getTextureIndex).toArray();
    }
}
