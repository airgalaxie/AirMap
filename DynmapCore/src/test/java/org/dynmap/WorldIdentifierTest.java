package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldIdentifierTest {
    @Test
    void preservesCanonicalRegistryIds() {
        assertEquals("minecraft:overworld", WorldIdentifier.canonicalId("minecraft:overworld"));
        assertEquals("example:dimensions/moon", WorldIdentifier.canonicalId("example:dimensions/moon"));
    }

    @Test
    void createsStableDistinctSafeStorageIds() {
        String overworld = WorldIdentifier.storageId("minecraft:overworld");
        assertEquals(overworld, WorldIdentifier.storageId("minecraft:overworld"));
        assertNotEquals(overworld, WorldIdentifier.storageId("minecraft:the_nether"));
        assertTrue(WorldIdentifier.isSafeStorageId(overworld));
        assertFalse(overworld.contains("/"));
        assertFalse(overworld.contains("\\"));
        assertFalse(overworld.contains(".."));
    }

    @Test
    void rejectsMalformedCanonicalIdsAtTheBoundary() {
        assertThrows(NullPointerException.class, () -> WorldIdentifier.canonicalId(null));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentifier.canonicalId(" "));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentifier.canonicalId("minecraft:bad\\path"));
        assertThrows(IllegalArgumentException.class, () -> WorldIdentifier.canonicalId(" minecraft:overworld"));
    }

    @Test
    void acceptsOnlySingleLegacyPathComponents() {
        assertTrue(WorldIdentifier.isSafeLegacyPathComponent("DIM-1"));
        assertTrue(WorldIdentifier.isSafeLegacyPathComponent("minecraft:overworld"));
        assertFalse(WorldIdentifier.isSafeLegacyPathComponent("../world"));
        assertFalse(WorldIdentifier.isSafeLegacyPathComponent("/world"));
        assertFalse(WorldIdentifier.isSafeLegacyPathComponent("world\\other"));
        assertFalse(WorldIdentifier.isSafeLegacyPathComponent(".."));
    }
}
