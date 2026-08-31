package org.dynmap.hdmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.PatchDefinition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ScratchLayerProbeTest {
    @Test
    void probeChestAndStatue() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "cache not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());

        DynmapBlockState chest = state(core, "minecraft:chest", "facing=north,type=single,waterlogged=false");
        DynmapBlockState statue = state(core, "minecraft:copper_golem_statue", "copper_golem_pose=standing,facing=north");
        DynmapBlockState bell = state(core, "minecraft:bell", "attachment=floor,facing=east,powered=false");

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        render(core, chest, "minecraft:textures/entity/chest/normal.png", cache + "/chest.png");
        render(core, statue, "minecraft:textures/entity/copper_golem/copper_golem.png", cache + "/statue.png");
        render(core, bell, "minecraft:textures/entity/bell/bell_body.png", cache + "/bell.png");
    }

    private static DynmapBlockState state(DynmapCore core, String name, String props) {
        var builder = new DynmapBlockState.Builder().setBlockName(name).setStateName(props);
        return builder.build();
    }

    private static void render(DynmapCore core, DynmapBlockState state, String texId, String out) throws Exception {
        HDBlockModel model = HDBlockModels.models_by_id_data[state.globalStateIndex];
        System.out.println("=== " + state.blockName + " [" + state.stateName + "] model=" + model);
        if (!(model instanceof HDBlockPatchModel pm)) return;
        PatchDefinition[] patches = pm.getPatches();
        System.out.println("patches=" + patches.length);
        byte[] png;
        try (InputStream in = core.getMinecraftResourceProvider().open(texId)) { png = in.readAllBytes(); }
        BufferedImage sprite = ImageIO.read(new ByteArrayInputStream(png));
        System.out.println("sprite=" + texId + " " + sprite.getWidth() + "x" + sprite.getHeight());
        int[] src = sprite.getRGB(0, 0, sprite.getWidth(), sprite.getHeight(), null, 0, sprite.getWidth());
        for (PatchDefinition p : patches) {
            System.out.printf("  quad %s tex=%s u=[%.3f,%.3f] v=[%.3f,%.3f] step=%s shadeStep=%s%n",
                    tr(p.x0, p.y0, p.z0) + " U" + tr(p.xu, p.yu, p.zu) + " V" + tr(p.xv, p.yv, p.zv),
                    p.textureindex, p.umin, p.umax, p.vmin, p.vmax, p.step, p.shadeStep);
        }
        int S = 96;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Arrays.sort(patches, (a, b) -> Double.compare(avgY(a), avgY(b)));
        for (PatchDefinition p : patches) {
            int[] xp = new int[4], yp = new int[4];
            for (int i = 0; i < 4; i++) {
                double[] pt = corner(p, i);
                xp[i] = (int) Math.round(pt[0] * S);
                yp[i] = (int) Math.round(pt[2] * S);
            }
            int minX = Math.max(0, min(xp)), maxX = Math.min(S - 1, max(xp));
            int minY = Math.max(0, min(yp)), maxY = Math.min(S - 1, max(yp));
            double shade = shadeFactor(p);
            for (int sy = minY; sy <= maxY; sy++) for (int sx = minX; sx <= maxX; sx++) {
                if (!inside(xp, yp, sx, sy)) continue;
                double cx = (sx + 0.5) / S, cz = (sy + 0.5) / S;
                double u = invU(p, cx, cz), v = invV(p, cx, cz);
                if (u < p.umin - 1e-9 || u > p.umax + 1e-9 || v < p.vmin - 1e-9 || v > p.vmax + 1e-9) continue;
                int tu = clamp((int) Math.floor(u * sprite.getWidth()), 0, sprite.getWidth() - 1);
                int tv = clamp((int) Math.floor((1.0 - v) * sprite.getHeight()), 0, sprite.getHeight() - 1);
                int c = src[tv * sprite.getWidth() + tu];
                int r = (int) ((c >> 16 & 0xFF) * shade), g = (int) ((c >> 8 & 0xFF) * shade), b = (int) ((c & 0xFF) * shade);
                img.setRGB(sx, sy, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }

    private static double[] corner(PatchDefinition p, int i) {
        double u = (i & 1) == 0 ? 0 : 1, v = (i & 2) == 0 ? 0 : 1;
        return new double[] { p.x0 + p.u.x * u + p.v.x * v,
                p.y0 + p.u.y * u + p.v.y * v,
                p.z0 + p.u.z * u + p.v.z * v };
    }

    private static double invU(PatchDefinition p, double cx, double cz) {
        double len = p.u.x * p.u.x + p.u.z * p.u.z;
        return len == 0 ? p.umin : ((cx - p.x0) * p.u.x + (cz - p.z0) * p.u.z) / len;
    }

    private static double invV(PatchDefinition p, double cx, double cz) {
        double len = p.v.x * p.v.x + p.v.z * p.v.z;
        return len == 0 ? p.vmin : ((cx - p.x0) * p.v.x + (cz - p.z0) * p.v.z) / len;
    }

    private static double avgY(PatchDefinition p) {
        return (p.y0 + (p.y0 + p.u.y) + (p.y0 + p.v.y) + (p.y0 + p.u.y + p.v.y)) / 4.0;
    }

    private static double shadeFactor(PatchDefinition p) {
        BlockStep s = p.shadeStep != null ? p.shadeStep : p.step;
        if (s == null) return 1.0;
        return switch (s) {
            case Y_MINUS -> 0.45;
            case X_PLUS, Z_PLUS -> 0.8;
            case X_MINUS, Z_MINUS -> 0.6;
            default -> 1.0;
        };
    }

    private static boolean inside(int[] xp, int[] yp, int x, int y) {
        boolean c = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            boolean a = (yp[i] > y) != (yp[j] > y);
            if (a && x < (double) (xp[j] - xp[i]) * (y - yp[i]) / (yp[j] - yp[i]) + xp[i]) c = !c;
        }
        return c;
    }

    private static int min(int[] a) { int m = a[0]; for (int v : a) if (v < m) m = v; return m; }
    private static int max(int[] a) { int m = a[0]; for (int v : a) if (v > m) m = v; return m; }
    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static String tr(double x, double y, double z) {
        return String.format("(%.3f,%.3f,%.3f)", x, y, z);
    }
}