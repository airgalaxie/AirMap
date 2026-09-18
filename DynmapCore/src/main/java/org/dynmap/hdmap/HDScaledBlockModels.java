package org.dynmap.hdmap;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

/** Immutable view of one fully published block-model generation. */
public class HDScaledBlockModels {
    private final HDBlockPatchModel[] patches;
    private final CustomBlockModel[] custom;

    public HDScaledBlockModels(int ignoredScale) {
        HDBlockModel[] source = HDBlockModels.models_by_id_data;
        HDBlockPatchModel[] newPatches = new HDBlockPatchModel[source.length];
        CustomBlockModel[] newCustom = new CustomBlockModel[source.length];
        for (int index = 0; index < source.length; index++) {
            HDBlockModel model = source[index];
            if (model instanceof HDBlockPatchModel patchModel) newPatches[index] = patchModel;
            else if (model instanceof CustomBlockModel customModel) newCustom[index] = customModel;
        }
        patches = newPatches;
        custom = newCustom;
    }

    public PatchDefinition[] getPatchModel(DynmapBlockState block, int x, int y, int z) {
        int index = block.globalStateIndex;
        return index < patches.length && patches[index] != null
                ? patches[index].getPatches(x, y, z) : null;
    }

    public CustomBlockModel getCustomBlockModel(DynmapBlockState block) {
        int index = block.globalStateIndex;
        return index < custom.length ? custom[index] : null;
    }
}
