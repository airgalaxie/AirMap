package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.BitSet;
import java.util.Map;
import org.dynmap.renderer.DynmapBlockState;
import org.junit.jupiter.api.Test;

class CustomBlockModelLifecycleTest {
    @Test
    void failedRendererDoesNotReplaceTheUsableModel() {
        DynmapBlockState state = new DynmapBlockState.Builder()
                .setBlockName("test:failed_renderer_" + System.nanoTime()).build();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        BitSet states = new BitSet();
        states.set(state.stateIndex);
        HDBlockPatchModel existing = new HDBlockPatchModel(
                state, states, new org.dynmap.utils.PatchDefinition[0], "test");

        CustomBlockModel failed = new CustomBlockModel(state, states,
                "org.dynmap.hdmap.DoesNotExist", Map.of(), "test");

        assertNull(failed.render);
        assertSame(existing, HDBlockModels.models_by_id_data[state.globalStateIndex]);
    }
}
