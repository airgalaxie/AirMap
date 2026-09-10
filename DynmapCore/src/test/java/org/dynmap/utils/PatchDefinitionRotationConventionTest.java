package org.dynmap.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies PatchDefinition.rotatePrecomputed rotates all three axes with the
 * Minecraft model convention (x=east, y=up, z=south).
 *
 * <p>rotateAround rotates direction and basis vectors about the origin.
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
    @Test
    void zPositive90RotatesPlusXToPlusY() {
        assertVec(0, 1, 0, rot(1, 0, 0, 0, 0, 90));
    }

    @Test
    void zPositive90RotatesPlusYToMinusX() {
        assertVec(-1, 0, 0, rot(0, 1, 0, 0, 0, 90));
    }

    @Test
    void zNegative90RotatesPlusXToMinusY() {
        assertVec(0, -1, 0, rot(1, 0, 0, 0, 0, -90));
    }

    /* ---- Minecraft model X rotation: y'=y cos + z sin, z'=z cos - y sin ---- */

    @Test
    void xPositive90RotatesPlusYToMinusZ() {
        assertVec(0, 0, -1, rot(0, 1, 0, 90, 0, 0));
    }

    @Test
    void xPositive90RotatesPlusZToPlusY() {
        assertVec(0, 1, 0, rot(0, 0, 1, 90, 0, 0));
    }

    @Test
    void xNegative90RotatesPlusYToPlusZ() {
        assertVec(0, 0, 1, rot(0, 1, 0, -90, 0, 0));
    }

    /* ---- Y rotation (unchanged control; right-hand about +Y): x'=x cos - z sin, z'=x sin + z cos ---- */

    @Test
    void yPositive90RotatesPlusXToPlusZ() {
        assertVec(0, 0, 1, rot(1, 0, 0, 0, 90, 0));
    }

    @Test
    void yPositive90RotatesPlusZToMinusX() {
        assertVec(-1, 0, 0, rot(0, 0, 1, 0, 90, 0));
    }

    /* ---- each axis leaves its own component fixed ---- */

    @Test
    void xRotationLeavesXComponentUnchanged() {
        assertEquals(1, rot(1, 1, 0, 90, 0, 0).x, 1.0e-9);
    }

    @Test
    void yRotationLeavesYComponentUnchanged() {
        assertEquals(1, rot(1, 1, -1, 0, 90, 0).y, 1.0e-9);
    }

    @Test
    void zRotationLeavesZComponentUnchanged() {
        assertEquals(-2, rot(1, 0, -2, 0, 0, 90).z, 1.0e-9);
    }

    @Test
    void y45UnitBasisProducesSymmetricVanillaRescaleFactors() {
        double[] factors = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            Vector3D unit = rot(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0,
                    0, 45, 0);
            double max = Math.max(Math.abs(unit.x), Math.max(Math.abs(unit.y), Math.abs(unit.z)));
            factors[axis] = 1 / max;
        }

        assertEquals(Math.sqrt(2), factors[0], 1.0e-9);
        assertEquals(1, factors[1], 1.0e-9);
        assertEquals(Math.sqrt(2), factors[2], 1.0e-9);
    }
}
