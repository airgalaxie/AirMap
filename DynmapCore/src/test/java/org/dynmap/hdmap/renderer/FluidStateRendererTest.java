package org.dynmap.hdmap.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dynmap.renderer.DynmapBlockState;
import org.junit.jupiter.api.Test;

class FluidStateRendererTest {
    @Test
    void liquidBlockLevelsMapToMinecraftFluidAmounts() {
        DynmapBlockState source = water("level=0", null, 0);
        assertEquals(8.0 / 9.0, FluidStateRenderer.getOwnHeight(source));
        for (int level = 1; level <= 7; level++) {
            assertEquals((8.0 - level) / 9.0,
                    FluidStateRenderer.getOwnHeight(water("level=" + level, source, level)));
        }
        assertEquals(8.0 / 9.0, FluidStateRenderer.getOwnHeight(water("level=8", source, 8)),
                "falling fluid keeps amount 8");
        assertEquals(8.0 / 9.0, FluidStateRenderer.getOwnHeight(water("level=15", source, 15)),
                "all falling LiquidBlock levels keep amount 8");
    }

    @Test
    void cornerUsesMinecraftWeightedAverage() {
        DynmapBlockState source = water("level=0", null, 0);
        DynmapBlockState air = DynmapBlockState.AIR;
        double ownHeight = 8.0 / 9.0;
        assertEquals((ownHeight * 10.0) / 12.0, FluidStateRenderer.getCornerHeight(
                source, ownHeight, 0.0, 0.0, air, air));
        assertEquals((ownHeight * 20.0) / 22.0, FluidStateRenderer.getCornerHeight(
                source, ownHeight, 0.0, ownHeight, air, air));
        assertEquals(1.0, FluidStateRenderer.getCornerHeight(
                source, ownHeight, 1.0, 0.0, air, air));
    }

    private static DynmapBlockState water(String stateName, DynmapBlockState base, int stateIndex) {
        DynmapBlockState.Builder builder = new DynmapBlockState.Builder()
                .setBlockName("test:water_" + System.identityHashCode(FluidStateRendererTest.class))
                .setStateName(stateName)
                .setStateIndex(stateIndex);
        if (base != null) builder.setBaseState(base);
        return builder.build();
    }
}
