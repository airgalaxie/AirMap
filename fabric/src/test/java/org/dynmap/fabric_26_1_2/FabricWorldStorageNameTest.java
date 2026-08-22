package org.dynmap.fabric_26_1_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class FabricWorldStorageNameTest {
    @Test
    void keepsCanonicalWorldNameReadable() {
        assertEquals("minecraft%3Aoverworld", FabricWorld.getStorageName("minecraft:overworld"));
        assertEquals("minecraft%3Athe_nether", FabricWorld.getStorageName("minecraft:the_nether"));
    }

    @Test
    void encodesNestedModDimensionWithoutLosingItsIdentity() {
        assertEquals("example%3Adimensions%2Fmoon",
                FabricWorld.getStorageName("example:dimensions/moon"));
    }

    @Test
    void encodesPercentAndUtf8BytesUnambiguously() {
        assertEquals("example%3A100%25%2F%C3%A4",
                FabricWorld.getStorageName("example:100%/ä"));
        assertNotEquals(FabricWorld.getStorageName("example:a/b"),
                FabricWorld.getStorageName("example:a%2Fb"));
    }
}
