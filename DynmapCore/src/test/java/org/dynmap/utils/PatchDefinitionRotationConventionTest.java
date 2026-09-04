package org.dynmap.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies PatchDefinition.rotatePrecomputed rotates all three axes with the
 * Minecraft model convention (x=east, y=up, z=south).
 *
 * <p>rotateAround rotates about the block center offsetCenter=(0.5,0.5,0.5),
 * so only vectors with a clean unit offset from the center are used here;
 * the test asserts on the resulting absolute coords.
 */
class PatchDefinitionRotationConventionTest {

    private static Vector3D rot(double x, double y, double z, double rx, double ry, double rz) {
        Vector3D vec = new Vector3D(x, y, z);
        PatchDefinition.rotateAround(vec, rx, ry, rz);
        return vec;
    }

    private static void assertVec(double ex, double ey, double ez, Vector3D v) {
        assertEquals(ex, v.x, 1.0e-9);
        assertEquals(ey, v.y, 1.0e-9);
        assertEquals(ez, v.z, 1.0e-9);
    }

    /* ---- Z rotation (Mojang right-hand about +Z): x'=x cos - y sin, y'=x sin + y cos ---- */
    /* offset (1,0,0) is the unit +X offset from center(0.5,0.5,0.5); input=(1.5,0.5,0.5). */

    @Test
    void zPositive90RotatesPlusXOffsetToPlusY() {
        assertVec(0.5, 1.5, 0.5, rot(1.5, 0.5, 0.5, 0, 0, 90));
    }

    @Test
    void zPositive90RotatesPlusYOffsetToMinusX() {
        assertVec(-0.5, 0.5, 0.5, rot(0.5, 1.5, 0.5, 0, 0, 90));
    }

    @Test
    void zNegative90RotatesPlusXOffsetToMinusY() {
        assertVec(0.5, -0.5, 0.5, rot(1.5, 0.5, 0.5, 0, 0, -90));
    }

    /* ---- Minecraft model X rotation: y'=y cos + z sin, z'=z cos - y sin ---- */

    @Test
    void xPositive90RotatesPlusYOffsetToMinusZ() {
        assertVec(0.5, 0.5, -0.5, rot(0.5, 1.5, 0.5, 90, 0, 0));
    }

    @Test
    void xPositive90RotatesPlusZOffsetToPlusY() {
        assertVec(0.5, 1.5, 0.5, rot(0.5, 0.5, 1.5, 90, 0, 0));
    }

    @Test
    void xNegative90RotatesPlusYOffsetToPlusZ() {
        assertVec(0.5, 0.5, 1.5, rot(0.5, 1.5, 0.5, -90, 0, 0));
    }

    /* ---- Y rotation (unchanged control; right-hand about +Y): x'=x cos - z sin, z'=x sin + z cos ---- */

    @Test
    void yPositive90RotatesPlusXOffsetToPlusZ() {
        assertVec(0.5, 0.5, 1.5, rot(1.5, 0.5, 0.5, 0, 90, 0));
    }

    @Test
    void yPositive90RotatesPlusZOffsetToMinusX() {
        assertVec(-0.5, 0.5, 0.5, rot(0.5, 0.5, 1.5, 0, 90, 0));
    }

    /* ---- each axis leaves its own offset component fixed ---- */

    @Test
    void xRotationLeavesXOffsetUnchanged() {
        assertEquals(1.5, rot(1.5, 1.5, 0.5, 90, 0, 0).x, 1.0e-9);
    }

    @Test
    void yRotationLeavesYOffsetUnchanged() {
        assertEquals(1.5, rot(1.5, 1.5, -1.5, 0, 90, 0).y, 1.0e-9);
    }

    @Test
    void zRotationLeavesZOffsetUnchanged() {
        assertEquals(-2.5, rot(1.5, 0.5, -2.5, 0, 0, 90).z, 1.0e-9);
    }
}
