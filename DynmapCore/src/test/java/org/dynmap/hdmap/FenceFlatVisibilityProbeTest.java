package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Narrow diagnostic for the reported fence loss in the scale-4 vertical perspective. */
class FenceFlatVisibilityProbeTest {
    @Test
    void realFencePatchesFallExactlyBetweenFlatPixelCenters() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "cache not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());
        DynmapBlockState fence = new DynmapBlockState.Builder().setBlockName("minecraft:oak_fence")
                .setStateName("east=true,north=false,south=true,waterlogged=false,west=false").build();
        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();
        PatchDefinition[] patches = ((HDBlockPatchModel) HDBlockModels.models_by_id_data[fence.globalStateIndex]).getPatches();

        int flat = countPixelRays(patches, 180, 90, 4, 0);
        int flatIncludingEdges = countPixelRays(patches, 180, 90, 4, 1);
        int flatHalfOpen = countPixelRays(patches, 180, 90, 4, 2);
        int surface = countPixelRays(patches, 135, 30, 16, 0);
        int surfaceHalfOpen = countPixelRays(patches, 135, 30, 16, 2);
        System.out.printf("oak fence patch hits: flat=%d flatIncludingEdges=%d flatHalfOpen=%d "
                + "surface=%d surfaceHalfOpen=%d patches=%d%n",
                flat, flatIncludingEdges, flatHalfOpen, surface, surfaceHalfOpen, patches.length);
        assertEquals(0, flat, "reported flat loss must reproduce at actual pixel centers");
        assertTrue(flatIncludingEdges > 0, "flat rays must reach fence patch edges, isolating the strict edge gate");
        assertTrue(flatHalfOpen > 0, "half-open patch ownership must restore flat fence coverage");
        assertTrue(surface > 0, "surface control must intercept the same model");
        assertEquals(surface, surfaceHalfOpen, "half-open ownership must preserve non-edge surface intersections");
    }

    private static int countPixelRays(PatchDefinition[] patches, double azimuth, double inclination, double scale,
            int edgeMode) {
        Matrix3D w2m = worldToMap(azimuth, inclination, scale);
        Matrix3D m2w = mapToWorld(azimuth, inclination, scale);
        Vector3D center = new Vector3D(0.5, 64.5, 0.5);
        w2m.transform(center);
        Vector3D direction = new Vector3D(0, 0, -129);
        m2w.transform(direction);
        int hits = 0;
        int minX = (int) Math.floor(center.x) - 20, minY = (int) Math.floor(center.y) - 20;
        for (int mx = minX; mx <= minX + 40; mx++) for (int my = minY; my <= minY + 40; my++) {
            Vector3D top = new Vector3D(mx + 0.5, my + 0.5, 128.5);
            m2w.transform(top);
            for (PatchDefinition patch : patches) if (hit(patch, top, direction, edgeMode)) hits++;
        }
        return hits;
    }

    private static boolean hit(PatchDefinition pd, Vector3D top, Vector3D direction, int edgeMode) {
        Vector3D v0 = new Vector3D(pd.x0, 64 + pd.y0, pd.z0);
        Vector3D cross = new Vector3D(direction); cross.crossProduct(pd.v);
        double det = pd.u.innerProduct(cross);
        switch (pd.sidevis) {
            case TOP, TOPFLIP, TOPFLIPV, TOPFLIPHV -> { if (det < 0.000001) return false; }
            case BOTTOM -> { if (det > -0.000001) return false; }
            case BOTH, FLIP -> { if (det > -0.000001 && det < 0.000001) return false; }
        }
        double inv = 1 / det;
        Vector3D offset = new Vector3D(top); offset.subtract(v0);
        double u = inv * offset.innerProduct(cross);
        if (edgeMode == 1 ? (u < pd.umin || u > pd.umax)
                : edgeMode == 2 ? !IsoHDPerspective.isWithinPatchBounds(u, pd.umin, pd.umax)
                : (u <= pd.umin || u >= pd.umax)) return false;
        offset.crossProduct(pd.u);
        double v = inv * direction.innerProduct(offset);
        double urel = (u - pd.umin) / (pd.umax - pd.umin);
        double vmax = pd.vmax + (pd.vmaxatumax - pd.vmax) * urel;
        double vmin = pd.vmin + (pd.vminatumax - pd.vmin) * urel;
        boolean withinV = edgeMode == 1 ? (v >= vmin && v <= vmax)
                : edgeMode == 2 ? IsoHDPerspective.isWithinPatchBounds(v, vmin, vmax)
                : (v > vmin && v < vmax);
        return withinV && inv * pd.v.innerProduct(offset) > 0.000001;
    }

    private static Matrix3D worldToMap(double azimuth, double inclination, double scale) {
        Matrix3D t = new Matrix3D(0, 0, -1, -1, 0, 0, 0, 1, 0);
        t.rotateXY(180 - azimuth); t.rotateYZ(90 - inclination);
        t.shearZ(0, Math.tan(Math.toRadians(90 - inclination)));
        t.scale(scale, scale, Math.sin(Math.toRadians(inclination))); return t;
    }

    private static Matrix3D mapToWorld(double azimuth, double inclination, double scale) {
        Matrix3D t = new Matrix3D(); t.scale(1 / scale, 1 / scale, 1 / Math.sin(Math.toRadians(inclination)));
        t.shearZ(0, -Math.tan(Math.toRadians(90 - inclination))); t.rotateYZ(-(90 - inclination));
        t.rotateXY(-180 + azimuth); t.multiply(new Matrix3D(0, -1, 0, 0, 0, 1, -1, 0, 0)); return t;
    }
}
