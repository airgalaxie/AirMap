package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.MapManager;
import org.dynmap.DynmapWorld;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapChunk;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.MapChunkCache;
import org.junit.jupiter.api.Test;

/** Exercises the real perspective light sampler and texture shader without resource downloads. */
class FlatLightingTest {
    private static final DynmapWorld WORLD = new DynmapWorld("flat-light-test", 384, 63, -64) {
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return null; }
        @Override public long getTime() { return 0; }
        @Override public boolean hasStorm() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isLoaded() { return true; }
        @Override public void setWorldUnloaded() { }
        @Override public int getLightLevel(int x, int y, int z) { return 15; }
        @Override public int getHighestBlockYAt(int x, int z) { return 64; }
        @Override public boolean canGetSkyLightLevel() { return true; }
        @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
        @Override public String getEnvironment() { return "normal"; }
        @Override public MapChunkCache getChunkCache(List<DynmapChunk> chunks) { return null; }
    };
    private static final MapChunkCache CACHE = new GenericMapChunkCache(null) {
        @Override public DynmapWorld getWorld() { return WORLD; }
    };
    private static final DynmapBlockState STONE = new DynmapBlockState.Builder()
            .setBlockName("airmap_test:lighting_stone").setAttenuatesLight(15).build();

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    static final class Scene {
        final int[] position = {0, 64, 0};
        final int sky, emitted;
        final boolean blocked;
        int surfaceY = 64;
        int neighborLight = -1;
        boolean terrace;
        int terraceX = 1, terraceZ;
        int obstructionHeight = 1;
        final Map<String, Integer> surfaceLights = new HashMap<>();
        final MapIterator iterator;
        final HDPerspectiveState perspective;
        final IsoHDPerspective projection;

        Scene(double inclination, int sky, int emitted, boolean blocked) throws Exception {
            this.sky = sky;
            this.emitted = emitted;
            this.blocked = blocked;
            HDBlockStateTextureMap.copyToStateIndex(STONE, HDBlockStateTextureMap.BLANK, BlockTransparency.OPAQUE);
            HDBlockStateTextureMap.copyToStateIndex(DynmapBlockState.AIR, HDBlockStateTextureMap.BLANK, BlockTransparency.TRANSPARENT);
            iterator = (MapIterator) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {MapIterator.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getWorldHeight" -> 384;
                        case "getWorldYMin" -> -64;
                        case "getY" -> position[1];
                        case "getBlockType" -> blockAt(position[0], position[1], position[2]);
                        case "getBlockTypeAt" -> {
                            if (args.length == 3) {
                                yield blockAt(position[0] + (int) args[0], position[1] + (int) args[1], position[2] + (int) args[2]);
                            }
                            BlockStep step = (BlockStep) args[0];
                            yield blockAt(position[0] + step.xoff, position[1] + step.yoff, position[2] + step.zoff);
                        }
                        case "getBlockLight" -> {
                            BlockStep step = (BlockStep) args[0];
                            yield lightAt(position[0] + step.xoff, position[1] + step.yoff, position[2] + step.zoff);
                        }
                        case "getBlockSkyLight" -> lightAt(position[0], position[1], position[2]) & 15;
                        case "getBlockEmittedLight" -> lightAt(position[0], position[1], position[2]) >> 8;
                        case "stepPosition", "unstepPosition" -> {
                            BlockStep step = (BlockStep) args[0];
                            int sign = method.getName().equals("stepPosition") ? 1 : -1;
                            position[0] += sign * step.xoff;
                            position[1] += sign * step.yoff;
                            position[2] += sign * step.zoff;
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            ConfigurationNode config = new ConfigurationNode();
            config.put("name", "lighting-test");
            config.put("inclination", inclination);
            config.put("scale", 16);
            projection = new IsoHDPerspective(null, config);
            Class<?> stateClass = Class.forName(IsoHDPerspective.class.getName() + "$OurPerspectiveState");
            Constructor<?> ctor = stateClass.getDeclaredConstructor(IsoHDPerspective.class, MapIterator.class, boolean.class, int.class);
            ctor.setAccessible(true);
            perspective = (HDPerspectiveState) ctor.newInstance(projection, iterator, false, 0);
            set(perspective, "blocktype", STONE);
            set(perspective, "y", 64);
        }

        DynmapBlockState blockAt(int x, int y, int z) {
            int height = surfaceY + (terrace && x * terraceX + z * terraceZ >= 1 ? 1 : 0);
            return y <= height || (blocked && y <= height + obstructionHeight && (x != 0 || z != 0)) ? STONE : DynmapBlockState.AIR;
        }

        int lightAt(int x, int y, int z) {
            if (blockAt(x, y, z) == STONE) return 0;
            if (surfaceLights.containsKey(x + "," + z)) return surfaceLights.get(x + "," + z);
            return neighborLight >= 0 && (x != 0 || z != 0) ? neighborLight : (emitted << 8) | sky;
        }
    }

    @Test
    void blockedNeighborUsesExposedFaceLightInFlatOnly() throws Exception {
        for (double inclination : new double[] {90, 60}) {
            Scene scene = new Scene(inclination, 10, 7, true);
            LightLevels sample = new LightLevels();
            scene.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, sample);
            assertEquals(inclination == 90 ? 10 : 0, sample.sky);
            assertEquals(inclination == 90 ? 7 : 0, sample.emitted);
            assertEquals(0, scene.position[0]);
            assertEquals(64, scene.position[1]);
            assertEquals(0, scene.position[2]);
        }
    }

    @Test
    void exposedNeighborsKeepTheirMinecraftLight() throws Exception {
        Scene scene = new Scene(90, 9, 5, false);
        LightLevels sample = new LightLevels();
        scene.perspective.getLightLevelsAtStep(BlockStep.X_MINUS, sample);
        assertEquals(9, sample.sky);
        assertEquals(5, sample.emitted);
        scene.neighborLight = 0;
        scene.perspective.getLightLevelsAtStep(BlockStep.X_MINUS, sample);
        assertEquals(0, sample.sky);
        assertEquals(0, sample.emitted);
    }

    @Test
    void flatTopHasNoArtificialHeightStripes() throws Exception {
        for (double inclination : new double[] {90, 60}) {
            for (int y : new int[] {64, 65}) {
                assertEquals(inclination == 90 ? 200 : (y == 64 ? 170 : 180), shadeTop(inclination, y));
            }
        }
    }

    private static int shadeTop(double inclination, int y) throws Exception {
        return shade(new Scene(inclination, 15, 0, false), y, null, 8, 8).getRed();
    }

    private static Color shade(Scene scene, int y, ShadowHDLighting lighting, int u, int v) throws Exception {
        return shade(scene, y, lighting, u, v, 0, true);
    }

    private static Color shade(Scene scene, int y, ShadowHDLighting lighting, int u, int v, int pass, boolean shaded) throws Exception {
        MapManager original = MapManager.mapman;
        try {
            MapManager manager = (MapManager) allocate(MapManager.class);
            set(manager, "core", new DynmapCore());
            MapManager.mapman = manager;
            scene.position[1] = y;
            if (!scene.terrace) scene.surfaceY = y;
            set(scene.perspective, "y", y);
            HDMap map = (HDMap) allocate(HDMap.class);
            set(map, "perspective", scene.projection);
            set(map, "lighting", lighting == null ? new DefaultHDLighting(null, new ConfigurationNode()) : lighting);
            TexturePackHDShader shader = new TexturePackHDShader(null, new ConfigurationNode());
            set(shader, "did_tp_load", true);
            TexturePackHDShader.ShaderState state = shader.new ShaderState(scene.iterator, map, CACHE, 16);
            Field colorField = state.getClass().getDeclaredField("c");
            colorField.setAccessible(true);
            ((Color) colorField.get(state)).setRGBA(200, 200, 200, 255);
            HDPerspectiveState pixel = (HDPerspectiveState) Proxy.newProxyInstance(FlatLightingTest.class.getClassLoader(),
                    new Class<?>[] {HDPerspectiveState.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getSubblockCoord")) return new int[] {u, 15, v};
                        if (method.getName().equals("getShade")) return shaded;
                        return method.invoke(scene.perspective, args);
                    });
            state.processBlock(pixel);
            Color result = new Color();
            state.getRayColor(result, pass);
            return result;
        } finally {
            MapManager.mapman = original;
        }
    }

    @Test
    void directionalSideShadingRemainsDistinct() throws Exception {
        Scene scene = new Scene(90, 15, 0, false);
        for (BlockStep step : new BlockStep[] {BlockStep.X_MINUS, BlockStep.X_PLUS}) {
            set(scene.perspective, "laststep", step);
            assertEquals(125, shade(scene, 64, null, 8, 8).getRed());
        }
        set(scene.perspective, "laststep", BlockStep.Y_PLUS);
        assertEquals(170, shade(scene, 64, null, 8, 8).getRed());
    }

    private static ShadowHDLighting smoothLighting() throws Exception {
        return lighting(true);
    }

    private static ShadowHDLighting lighting(boolean smooth) throws Exception {
        return lighting(smooth, false);
    }

    private static ShadowHDLighting lighting(boolean smooth, boolean nightAndDay) throws Exception {
        ConfigurationNode config = new ConfigurationNode();
        config.put("shadowstrength", 1.0);
        config.put("smooth-lighting", smooth);
        config.put("night-and-day", nightAndDay);
        if (nightAndDay) config.put("ambientlight", 4);
        return configuredLighting(config, false, false);
    }

    private static ShadowHDLighting configuredLighting(ConfigurationNode config, boolean globalSmooth, boolean globalBrightness) throws Exception {
        MapManager original = MapManager.mapman;
        try {
            MapManager manager = (MapManager) allocate(MapManager.class);
            DynmapCore core = new DynmapCore();
            set(core, "smoothlighting", globalSmooth);
            set(manager, "core", core);
            set(manager, "useBrightnessTable", globalBrightness);
            MapManager.mapman = manager;
            return new ShadowHDLighting(null, config);
        } finally {
            MapManager.mapman = original;
        }
    }

    @Test
    void smoothingHonorsGlobalDefaultAndProfileOverride() throws Exception {
        Scene scene = new Scene(90, 0, 0, false);
        scene.surfaceLights.put("1,1", 15);
        for (boolean global : new boolean[] {false, true}) {
            for (Boolean local : new Boolean[] {null, false, true}) {
                ConfigurationNode config = new ConfigurationNode();
                config.put("shadowstrength", 1.0);
                if (local != null) config.put("smooth-lighting", local);
                ShadowHDLighting lighting = configuredLighting(config, global, false);
                int middle = shade(scene, 64, lighting, 8, 8).getRed();
                int corner = shade(scene, 64, lighting, 15, 15).getRed();
                boolean expectedSmooth = local == null ? global : local;
                if (expectedSmooth) assertTrue(corner > middle + 30);
                else assertEquals(middle, corner, "Disabled smoothing must use only the hit's light");
            }
        }
    }

    @Test
    void flatShadersHonorBrightnessProfileOverridesWithoutChangingSurface() throws Exception {
        MapManager original = MapManager.mapman;
        try {
            for (boolean global : new boolean[] {false, true}) {
                for (Boolean local : new Boolean[] {null, false, true}) {
                    ConfigurationNode config = new ConfigurationNode();
                    config.put("shadowstrength", 1.0);
                    if (local != null) config.put("use-brightness-table", local);
                    ShadowHDLighting lighting = configuredLighting(config, false, global);
                    MapManager manager = (MapManager) allocate(MapManager.class);
                    set(manager, "core", new DynmapCore());
                    set(manager, "useBrightnessTable", global);
                    MapManager.mapman = manager;
                    for (double inclination : new double[] {90, 60}) {
                        Scene scene = new Scene(inclination, 9, 0, false);
                        HDMap map = (HDMap) allocate(HDMap.class);
                        set(map, "perspective", scene.projection);
                        set(map, "lighting", lighting);
                        TexturePackHDShader texture = new TexturePackHDShader(null, new ConfigurationNode());
                        set(texture, "did_tp_load", true);
                        HDShader colors = (DefaultHDShader) allocate(DefaultHDShader.class);
                        set(colors, "biomecolored", DefaultHDShader.BiomeColorOption.NONE);
                        for (HDShader shader : new HDShader[] {texture, colors}) {
                            HDShaderState state = shader.getStateInstance(map, CACHE, scene.iterator, 16);
                            boolean expectedWorld = inclination == 90 && local != null ? local : global;
                            if (expectedWorld) assertSame(WORLD.getBrightnessTable(), state.getLightingTable());
                            else assertNull(state.getLightingTable());
                        }
                    }
                }
            }
        } finally {
            MapManager.mapman = original;
        }
    }

    @Test
    void shadowStrengthAmbientAndWorldTableKeepTheirConfiguredEffects() throws Exception {
        Scene scene = new Scene(90, 9, 0, false);
        ConfigurationNode config = new ConfigurationNode();
        config.put("shadowstrength", 0.0);
        config.put("smooth-lighting", true);
        config.put("use-brightness-table", false);
        assertEquals(200, shade(scene, 64, configuredLighting(config, false, true), 8, 8).getRed());
        config.put("shadowstrength", 1.0);
        int classic = shade(scene, 64, configuredLighting(config, false, true), 8, 8).getRed();
        assertTrue(classic < 200);
        config.put("ambientlight", 4);
        assertTrue(shade(scene, 64, configuredLighting(config, false, true), 8, 8).getRed() < classic);
        config.put("ambientlight", 15);
        config.put("use-brightness-table", true);
        Color expected = new Color(200, 200, 200);
        expected.scaleRGB(ShadowHDLighting.computeLightScale(0, 9, WORLD.getBrightnessTable()));
        assertEquals(expected.getARGB(), shade(scene, 64, configuredLighting(config, false, false), 8, 8).getARGB());
    }

    @Test
    void smoothingKeepsValidLightSeparateFromBoundedOcclusion() throws Exception {
        ShadowHDLighting lighting = smoothLighting();
        for (int[] light : new int[][] {{15, 0}, {9, 0}, {4, 12}, {0, 0}}) {
            Color open = shade(new Scene(90, light[0], light[1], false), 64, lighting, 0, 0);
            Color blocked = shade(new Scene(90, light[0], light[1], true), 64, lighting, 0, 0);
            assertTrue(blocked.getRed() <= open.getRed());
            assertTrue(blocked.getRed() > 0, "Occlusion must not reintroduce solid-cell zero light");
            LightLevels sample = new LightLevels();
            Scene scene = new Scene(90, light[0], light[1], true);
            scene.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, sample);
            assertEquals(light[0], sample.sky);
            assertEquals(light[1], sample.emitted);
        }
    }

    @Test
    void diagonalSamplesKeepLightAndRestoreIterator() throws Exception {
        Scene scene = new Scene(90, 15, 0, false);
        scene.surfaceLights.put("1,1", (6 << 8) | 8);
        LightLevels sample = new LightLevels();
        scene.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, BlockStep.Z_PLUS, sample);
        assertEquals(8, sample.sky);
        assertEquals(6, sample.emitted);
        assertEquals(0, scene.position[0]);
        assertEquals(64, scene.position[1]);
        assertEquals(0, scene.position[2]);
        Scene blocked = new Scene(90, 10, 7, true);
        blocked.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, BlockStep.Z_PLUS, sample);
        assertEquals(10, sample.sky);
        assertEquals(7, sample.emitted);
        blocked.obstructionHeight = 2;
        blocked.surfaceLights.put("1,1", 15);
        blocked.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, BlockStep.Z_PLUS, sample);
        assertEquals(10, sample.sky, "A still-covered neighbor must retain the safe center fallback");
        assertEquals(7, sample.emitted);
    }

    @Test
    void flatInterpolatesDiagonalLightWhenSmoothingIsEnabled() throws Exception {
        Scene scene = new Scene(90, 0, 0, false);
        scene.surfaceLights.put("1,1", 15);
        ShadowHDLighting lighting = lighting(true);
        int middle = shade(scene, 64, lighting, 8, 8).getRed();
        int corner = shade(scene, 64, lighting, 15, 15).getRed();
        assertTrue(corner > middle + 30, "A lit diagonal must contribute to the flat corner");
        assertTrue(corner < 200, "Interpolation must stay within its source lights");
        Scene surface = new Scene(60, 0, 0, false);
        surface.surfaceLights.put("1,1", 15);
        assertEquals(shade(surface, 64, lighting, 8, 8).getARGB(),
                shade(surface, 64, lighting, 15, 15).getARGB());
    }

    @Test
    void minecraftLightChangesBlendAcrossBlockBoundaries() throws Exception {
        ShadowHDLighting lighting = lighting(true);
        Scene scene = new Scene(90, 10, 0, false);
        for (int x = 1; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) scene.surfaceLights.put(x + "," + z, 15);
        }
        int previous = -1;
        for (int pixel = 0; pixel < 32; pixel++) {
            scene.position[0] = pixel / 16;
            int value = shade(scene, 64, lighting, pixel % 16, 8).getRed();
            if (previous >= 0) {
                assertTrue(value >= previous, "Light must blend monotonically");
                assertTrue(value - previous <= 10, "No hard boundary between blocks");
            }
            previous = value;
        }
    }

    @Test
    void uniformSkyLightRetainsSoftTerrainOcclusion() throws Exception {
        Scene scene = new Scene(90, 15, 0, false);
        scene.terrace = true;
        ShadowHDLighting lighting = lighting(true);
        int previous = 200;
        for (int u = 0; u < 16; u++) {
            int value = shade(scene, 64, lighting, u, 8).getRed();
            assertTrue(value <= previous, "Occlusion should form a broad gradient towards the terrace");
            assertTrue(previous - value <= 6, "No narrow black contour");
            assertTrue(value >= 170, "A single step must retain at least 85% of the surface light");
            previous = value;
        }
        assertTrue(previous < 190, "The softened step must remain visible");
        scene.position[0] = 1;
        assertEquals(200, shade(scene, 65, lighting, 0, 8).getRed());
    }

    @Test
    void flatReliefDistinguishesSlopeDirectionsWithoutChangingLight() throws Exception {
        ShadowHDLighting lighting = lighting(true);
        Scene away = new Scene(90, 15, 0, false);
        away.terrace = true;
        Scene toward = new Scene(90, 15, 0, false);
        toward.terrace = true;
        toward.terraceX = -1;
        int dark = shade(away, 64, lighting, 15, 8).getRed();
        int light = shade(toward, 64, lighting, 0, 8).getRed();
        assertTrue(light >= dark + 10, "Opposite slopes need a consistent directional cue");
        assertTrue(dark >= 170 && light < 200, "Both directions retain gentle contact shading");
        LightLevels levels = new LightLevels();
        away.perspective.getLightLevels(levels);
        assertEquals(15, levels.sky);
        assertEquals(0, levels.emitted);
        // A translated terrace must not acquire height bands or a different contrast.
        away.surfaceY = 65;
        assertEquals(dark, shade(away, 65, lighting, 15, 8).getRed());
    }

    @Test
    void occlusionRespectsSmoothingModelShadeAndOtherPerspectives() throws Exception {
        Scene terrace = new Scene(90, 15, 0, false);
        terrace.terrace = true;
        assertEquals(200, shade(terrace, 64, lighting(false), 15, 8).getRed());
        assertTrue(shade(terrace, 64, lighting(true), 15, 8).getRed() < 200);
        assertEquals(200, shade(terrace, 64, lighting(true), 15, 8, 0, false).getRed());
        Scene surface = new Scene(60, 15, 0, true);
        assertEquals(1.0, surface.perspective.getAmbientOcclusion(new int[] {15, 15, 8}));
        set(terrace.perspective, "blocktype", DynmapBlockState.AIR);
        assertEquals(1.0, terrace.perspective.getAmbientOcclusion(new int[] {15, 15, 8}));
    }

    @Test
    void terraceUsesExposedNeighborLightForSoftHeightTransitions() throws Exception {
        Scene scene = new Scene(90, 10, 0, false);
        scene.terrace = true;
        for (int x = 1; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) scene.surfaceLights.put(x + "," + z, 15);
        }
        LightLevels neighbor = new LightLevels();
        scene.perspective.getLightLevelsAtStep(BlockStep.X_PLUS, neighbor);
        assertEquals(15, neighbor.sky);
        assertEquals(64, scene.position[1]);
        ShadowHDLighting lighting = lighting(true);
        int previous = -1;
        for (int pixel = 0; pixel < 32; pixel++) {
            scene.position[0] = pixel / 16;
            int value = shade(scene, 64 + pixel / 16, lighting, pixel % 16, 8).getRed();
            if (previous >= 0 && pixel != 16) {
                assertTrue(Math.abs(value - previous) <= 10, "Occlusion must vary across the face, not form a narrow line");
            }
            assertTrue(value > 30, "A lit terrace must retain valid surface light");
            previous = value;
        }
    }

    @Test
    void flatPreservesNightDayAndUnshadedModelBehavior() throws Exception {
        ShadowHDLighting lighting = lighting(false, true);
        Scene sky = new Scene(90, 15, 0, false);
        Color night = shade(sky, 64, lighting, 8, 8, 0, true);
        Color day = shade(sky, 64, lighting, 8, 8, 1, true);
        assertTrue(night.getRed() < day.getRed());
        assertEquals(200, day.getRed());
        Scene block = new Scene(90, 0, 12, false);
        assertEquals(shade(block, 64, lighting, 8, 8, 0, true).getARGB(),
                shade(block, 64, lighting, 8, 8, 1, true).getARGB());
        Scene unshaded = new Scene(90, 10, 0, false);
        unshaded.surfaceLights.put("1,1", 15);
        assertEquals(shade(unshaded, 64, lighting, 8, 8, 1, false).getARGB(),
                shade(unshaded, 64, lighting, 15, 15, 1, false).getARGB());
    }

    @Test
    void writeTerrainLightingComparisonFixture() throws Exception {
        String output = System.getenv("AIRMAP_FLAT_TERRAIN_IMAGE");
        if (output == null) return;
        ShadowHDLighting lighting = lighting(true);
        BufferedImage image = new BufferedImage(790, 350, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(java.awt.Color.BLACK);
        graphics.drawString("Flat Core: Minecraft surface light on a one-block terrace (albedo RGB 200)", 15, 22);
        graphics.drawString("sky 15 everywhere", 15, 80);
        graphics.drawString("sky 10 / 15", 15, 180);
        graphics.drawString("sky 10 + block 12 / sky 15", 15, 280);
        for (int row = 0; row < 3; row++) {
            Scene scene = new Scene(90, row == 0 ? 15 : 10, row == 2 ? 12 : 0, false);
            scene.terrace = true;
            if (row != 0) {
                for (int x = 1; x <= 4; x++) {
                    for (int z = -1; z <= 2; z++) scene.surfaceLights.put(x + "," + z, 15);
                }
            }
            for (int px = 0; px < 64; px++) {
                for (int pz = 0; pz < 16; pz++) {
                    scene.position[0] = px / 16 - 1;
                    Color pixel = shade(scene, scene.position[0] >= 1 ? 65 : 64, lighting, px % 16, pz);
                    graphics.setColor(new java.awt.Color(pixel.getARGB(), true));
                    graphics.fillRect(235 + px * 8, 45 + row * 100 + pz * 4, 8, 4);
                }
            }
        }
        graphics.dispose();
        Path path = Path.of(output);
        Files.createDirectories(path.toAbsolutePath().getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    /** Optional reproducible image of actual shader output; run against either source revision. */
    @Test
    void writeLightingComparisonFixture() throws Exception {
        String output = System.getenv("AIRMAP_FLAT_LIGHTING_IMAGE");
        if (output == null) return;
        ShadowHDLighting lighting = smoothLighting();
        BufferedImage image = new BufferedImage(610, 380, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(java.awt.Color.BLACK);
        graphics.drawString("Flat lighting: real Core shader, neutral albedo RGB 200", 15, 22);
        graphics.drawString("Open neighbors", 240, 48);
        graphics.drawString("Blocked neighbors", 425, 48);
        int[][] lights = {{15, 0}, {9, 0}, {4, 12}};
        for (int row = 0; row < lights.length; row++) {
            graphics.setColor(java.awt.Color.BLACK);
            graphics.drawString("sky=" + lights[row][0] + ", block=" + lights[row][1], 15, 100 + row * 100);
            for (int col = 0; col < 2; col++) {
                Scene scene = new Scene(90, lights[row][0], lights[row][1], col == 1);
                for (int u = 0; u < 32; u++) {
                    for (int v = 0; v < 16; v++) {
                        // Two adjacent heights expose the legacy parity stripe as well.
                        Color pixel = shade(scene, 64 + u / 16, lighting, u % 16, v);
                        graphics.setColor(new java.awt.Color(pixel.getARGB(), true));
                        graphics.fillRect(225 + col * 185 + u * 4, 65 + row * 100 + v * 4, 4, 4);
                    }
                }
            }
        }
        graphics.dispose();
        Path path = Path.of(output);
        Files.createDirectories(path.toAbsolutePath().getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}
