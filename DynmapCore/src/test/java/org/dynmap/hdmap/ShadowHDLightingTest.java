package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShadowHDLightingTest {
    private static final int[] OVERWORLD_TABLE = {
        0, 4, 9, 15, 21, 28, 36, 45, 56, 69, 85, 104, 128, 158, 199, 256
    };

    @Test
    void boostsBlockLightWithoutBoostingSkyLight() {
        assertEquals(256, ShadowHDLighting.computeLightScale(14, 0, OVERWORLD_TABLE));
        assertEquals(179, ShadowHDLighting.computeLightScale(12, 0, OVERWORLD_TABLE));
        assertEquals(128, ShadowHDLighting.computeLightScale(0, 12, OVERWORLD_TABLE));
    }

    @Test
    void addsSkyAndBlockContributionsAndClampsTheResult() {
        assertEquals(204, ShadowHDLighting.computeLightScale(10, 10, OVERWORLD_TABLE));
        assertEquals(256, ShadowHDLighting.computeLightScale(12, 12, OVERWORLD_TABLE));
    }

    @Test
    void countsDimensionAmbientOnlyOnce() {
        int[] ambientTable = {
            25, 29, 34, 39, 44, 50, 57, 65, 75, 87, 101, 119, 140, 167, 204, 256
        };

        assertEquals(25, ShadowHDLighting.computeLightScale(0, 0, ambientTable));
        assertEquals(71, ShadowHDLighting.computeLightScale(4, 4, ambientTable));
    }
}
