package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IsoHDPerspectivePatchBoundaryTest {
    @Test
    void patchBoundsAreHalfOpen() {
        assertFalse(IsoHDPerspective.isWithinPatchBounds(Math.nextDown(0.25), 0.25, 0.5));
        assertTrue(IsoHDPerspective.isWithinPatchBounds(0.25, 0.25, 0.5));
        assertTrue(IsoHDPerspective.isWithinPatchBounds(Math.nextDown(0.5), 0.25, 0.5));
        assertFalse(IsoHDPerspective.isWithinPatchBounds(0.5, 0.25, 0.5));
    }

    @Test
    void exactlyOneAdjacentPatchOwnsTheirSharedEdge() {
        double edge = 0.5;
        assertFalse(IsoHDPerspective.isWithinPatchBounds(edge, 0.25, edge));
        assertTrue(IsoHDPerspective.isWithinPatchBounds(edge, edge, 0.75));
    }
}
