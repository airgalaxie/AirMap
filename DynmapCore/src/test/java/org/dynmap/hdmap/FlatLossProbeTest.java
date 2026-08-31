package org.dynmap.hdmap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Decisive probe: trace one normal chest (block at x=0,y=64,z=0) through the
 * real IsoHDPerspective geometry for the two maps used in the realtest:
 *   FLAT    = iso_S_90_lowres  (azimuth=180, inclination=90, scale=4)
 *   SURFACE = iso_SE_30_hires  (azimuth=135, inclination=30, scale=16)
 * Uses the real chest PatchDefinitions (loader output) + the exact
 * map_to_world construction and handlePatch math from IsoHDPerspective.
 */
class FlatLossProbeTest {

    static final double CHEST_Y = 64.0;
    static final double WORLD_TOP = 128.0;
    static final double MINY = 0.0;

    @Test
    void traceChestFlatVsSurface() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "cache not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());

        DynmapBlockState chest = new DynmapBlockState.Builder()
                .setBlockName("minecraft:chest").setStateName("facing=north,type=single,waterlogged=false")
                .build();

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        HDBlockModel model = HDBlockModels.models_by_id_data[chest.globalStateIndex];
        System.out.println("model=" + model);
        if (!(model instanceof HDBlockPatchModel pm)) { System.out.println("NOT a patch model"); return; }
        PatchDefinition[] patches = pm.getPatches();
        System.out.println("chest patches=" + patches.length);

        run("FLAT    iso_S_90_lowres",   180.0, 90.0, 4.0, patches);
        run("SURFACE iso_SE_30_hires",   135.0, 30.0, 16.0, patches);
    }

    private static void run(String label, double azimuth, double inclination, double scale, PatchDefinition[] patches) {
        Matrix3D m2w = mapToWorld(azimuth, inclination, scale);
        Matrix3D w2m = worldToMap(azimuth, inclination, scale);
        System.out.println("\n=== " + label + " azimuth=" + azimuth + " inclination=" + inclination + " scale=" + scale);

        /* direction = m2w * (0,0,(miny-0.5)-(height+0.5)) */
        Vector3D dir = new Vector3D(0.0, 0.0, (MINY - 0.5) - (WORLD_TOP + 0.5));
        m2w.transform(dir);

        /* Collect map columns that the chest's y=64.9 top plane projects onto
         * (dense sweep over the block footprint in world space x,z in [0,1]^2). */
        List<double[]> columns = new ArrayList<>();
        for (double wx = 0.02; wx < 1.0; wx += 0.025) {
            for (double wz = 0.02; wz < 1.0; wz += 0.025) {
                Vector3D p = new Vector3D(wx, CHEST_Y + 0.9, wz);
                w2m.transform(p);
                double[] col = { p.x, p.y };
                boolean dup = false;
                for (double[] c : columns) if (Math.abs(c[0] - col[0]) < 1e-9 && Math.abs(c[1] - col[1]) < 1e-9) { dup = true; break; }
                if (!dup) columns.add(col);
            }
        }
        System.out.println("sampled " + columns.size() + " map columns over chest footprint");

        int raysWithHit = 0;
        int totalHits = 0;
        List<String> details = new ArrayList<>();
        for (double[] col : columns) {
            Vector3D top = new Vector3D(col[0], col[1], WORLD_TOP + 0.5);
            m2w.transform(top);
            List<int[]> hits = new ArrayList<>(); // {patchIndex, sidevisGate ok?}
            for (int pi = 0; pi < patches.length; pi++) {
                PatchDefinition pd = patches[pi];
                Hit h = handlePatch(pd, top, dir, CHEST_Y);
                if (h != null) hits.add(new int[] { pi, 1 });
            }
            if (!hits.isEmpty()) {
                raysWithHit++;
                totalHits += hits.size();
                if (details.size() < 12) {
                    List<int[]> sorted = new ArrayList<>(hits);
                    sorted.sort((a, b) -> Double.compare(hitT(patches[a[0]], top, dir, CHEST_Y), hitT(patches[b[0]], top, dir, CHEST_Y)));
                    StringBuilder sb = new StringBuilder();
                    for (int[] h : sorted) {
                        PatchDefinition pd = patches[h[0]];
                        Vector3D v0 = new Vector3D(pd.x0, CHEST_Y + pd.y0, pd.z0);
                        sb.append(String.format("patch#%d %s tex=%d tt=%.3f u=[%.3f,%.3f] v=[%.3f,%.3f] step=%s | ",
                                h[0], tr(pd.x0, pd.y0, pd.z0), pd.textureindex, hitT(pd, top, dir, CHEST_Y),
                                pd.umin, pd.umax, pd.vmin, pd.vmax, pd.step));
                    }
                    details.add(sb.toString());
                }
            }
        }
        System.out.println("rays with >=1 chest patch hit: " + raysWithHit + " / " + columns.size());
        System.out.println("total patch-level hits: " + totalHits);
        if (raysWithHit == 0) {
            System.out.println("=> ZERO chest patch intercepts: first loss point is handlePatch interception (det/u/v/tt fail for all patches).");
        } else {
            System.out.println("=> chest patches ARE intercepted. First-hit(s):");
            for (String d : details) System.out.println("    " + d);
        }
    }

    static class Hit { double det, u, v, tt; }

    /** Exact replica of IsoHDPerspective.handlePatch (private method). */
    private static Hit handlePatch(PatchDefinition pd, Vector3D top, Vector3D direction, double blockY) {
        Vector3D v0 = new Vector3D(pd.x0, blockY + pd.y0, pd.z0);
        Vector3D dCrossUv = new Vector3D(direction);
        dCrossUv.crossProduct(pd.v);
        double det = pd.u.innerProduct(dCrossUv);
        switch (pd.sidevis) {
            case TOP:
            case TOPFLIP:
            case TOPFLIPV:
            case TOPFLIPHV:
                if (det < 0.000001) return null;
                break;
            case BOTTOM:
                if (det > -0.000001) return null;
                break;
            case BOTH:
            case FLIP:
                if ((det > -0.000001) && (det < 0.000001)) return null;
                break;
        }
        double invDet = 1.0 / det;
        Vector3D vS = new Vector3D(top);
        vS.subtract(v0);
        double u = invDet * vS.innerProduct(dCrossUv);
        if ((u <= pd.umin) || (u >= pd.umax)) return null;
        vS.crossProduct(pd.u);
        double v = invDet * direction.innerProduct(vS);
        double urel = (u > pd.umin) ? ((u - pd.umin) / (pd.umax - pd.umin)) : 0.0;
        double vmaxatu = pd.vmax + (pd.vmaxatumax - pd.vmax) * urel;
        double vminatu = pd.vmin + (pd.vminatumax - pd.vmin) * urel;
        if ((v <= vminatu) || (v >= vmaxatu)) return null;
        double tt = invDet * pd.v.innerProduct(vS);
        if (tt <= 0.000001) return null;
        Hit h = new Hit(); h.det = det; h.u = u; h.v = v; h.tt = tt;
        return h;
    }

    private static double hitT(PatchDefinition pd, Vector3D top, Vector3D direction, double blockY) {
        Hit h = handlePatch(pd, top, direction, blockY);
        return h == null ? Double.POSITIVE_INFINITY : h.tt;
    }

    /** Exact replica of IsoHDPerspective constructor transform assignments. */
    private static Matrix3D worldToMap(double azimuth, double inclination, double scale) {
        Matrix3D t = new Matrix3D(0.0, 0.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        t.rotateXY(180 - azimuth);
        t.rotateYZ(90.0 - inclination);
        t.shearZ(0, Math.tan(Math.toRadians(90.0 - inclination)));
        t.scale(scale, scale, Math.sin(Math.toRadians(inclination)));
        return t;
    }

    private static Matrix3D mapToWorld(double azimuth, double inclination, double scale) {
        Matrix3D t = new Matrix3D();
        t.scale(1.0 / scale, 1.0 / scale, 1 / Math.sin(Math.toRadians(inclination)));
        t.shearZ(0, -Math.tan(Math.toRadians(90.0 - inclination)));
        t.rotateYZ(-(90.0 - inclination));
        t.rotateXY(-180 + azimuth);
        Matrix3D coordswap = new Matrix3D(0.0, -1.0, 0.0, 0.0, 0.0, 1.0, -1.0, 0.0, 0.0);
        t.multiply(coordswap);
        return t;
    }

    private static String tr(double x, double y, double z) {
        return String.format("(%.3f,%.3f,%.3f)", x, y, z);
    }
}