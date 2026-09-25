package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

class HDMapRenderWorkIdentityTest {
    @Test
    void compatibleOutputsMustUseTheSamePerspectiveBoostAndTileScale() throws Exception {
        HDMap base = map(1, 0);
        HDMap same = map(1, 0);
        HDMap differentBoost = map(2, 0);
        HDMap differentScale = map(1, 1);

        assertTrue(base.sharesRenderWork(same));
        assertFalse(base.sharesRenderWork(differentBoost));
        assertFalse(base.sharesRenderWork(differentScale));
    }

    private static HDMap map(int boostZoom, int tileScale) throws Exception {
        Unsafe unsafe = unsafe();
        HDMap map = (HDMap) unsafe.allocateInstance(HDMap.class);
        setInt(map, "boostzoom", boostZoom);
        setInt(map, "tilescale", tileScale);
        return map;
    }

    private static void setInt(HDMap map, String name, int value) throws Exception {
        Field field = HDMap.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(map, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
