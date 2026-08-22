package org.dynmap.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class FabricWorldStorageNameTest {
    @Test
    void keepsCanonicalWorldNameReadable() {
        assertEquals("minecraft@overworld", FabricWorld.getStorageName("minecraft:overworld"));
        assertEquals("minecraft@the_nether", FabricWorld.getStorageName("minecraft:the_nether"));
    }

    @Test
    void encodesNestedModDimensionWithoutLosingItsIdentity() {
        assertEquals("example@dimensions+moon",
                FabricWorld.getStorageName("example:dimensions/moon"));
    }

    @Test
    void keepsDifferentValidRegistryIdsDistinct() {
        assertNotEquals(FabricWorld.getStorageName("example:a/b"),
                FabricWorld.getStorageName("example:a_b"));
    }
}
