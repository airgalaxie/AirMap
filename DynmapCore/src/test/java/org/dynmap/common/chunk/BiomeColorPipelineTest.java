package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.MapChunkCache.HiddenChunkStyle;
import org.dynmap.utils.VisibilityLimit;
import org.junit.jupiter.api.Test;

/**
 * Proves the platform-independent vanilla color pipeline:
 * grass/foliage overrides + BiomeSpecialEffects modifier math live entirely in the core.
 */
class BiomeColorPipelineTest {
    private static final DynmapWorld WORLD = new DynmapWorld("test", 384, 63, -64) {
        @Override public boolean isNether() { return false; }
        @Override public org.dynmap.DynmapLocation getSpawnLocation() { return new org.dynmap.DynmapLocation("test", 0, 0, 0); }
        @Override public long getTime() { return 0; }
        @Override public boolean hasStorm() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isLoaded() { return true; }
        @Override public void setWorldUnloaded() { }
        @Override public int getLightLevel(int x, int y, int z) { return 15; }
        @Override public int getHighestBlockYAt(int x, int z) { return 63; }
        @Override public boolean canGetSkyLightLevel() { return false; }
        @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
        @Override public String getEnvironment() { return "normal"; }
        @Override public MapChunkCache getChunkCache(java.util.List<org.dynmap.DynmapChunk> chunks) { return null; }
    };

    private static GenericMapChunkCache cache() {
        GenericMapChunkCache cache = new GenericMapChunkCache(new GenericChunkCache(4, false)) {
            @Override public boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome) { return true; }
            @Override public boolean isDoneLoading() { return true; }
            @Override public boolean isEmpty() { return false; }
            @Override public void unloadChunks() { }
            @Override public boolean isEmptySection(int sx, int sy, int sz) { return false; }
            @Override public org.dynmap.utils.MapIterator getIterator(int x0, int y0, int z0) { return null; }
            @Override public void setHiddenFillStyle(HiddenChunkStyle style) { }
            @Override public void setVisibleRange(VisibilityLimit limit) { }
            @Override public void setHiddenRange(VisibilityLimit limit) { }
        };
        cache.setChunks(WORLD, new ArrayList<>());
        return cache;
    }

    private static BiomeMap biome(String rl) {
        return new BiomeMap(BiomeMap.NO_INDEX, "test", 0.8, 0.4, "airmap-test:" + rl);
    }

    @Test
    void defaultGrassUsesColormapLookup() {
        GenericMapChunkCache cache = cache();
        int[] colormap = new int[256 * 256];
        Arrays.fill(colormap, 0x91BD59);
        assertEquals(0x91BD59, cache.getGrassColor(biome("plains"), colormap, 10, 10),
                "biome without override/modifier must resolve through the grass colormap");
    }

    @Test
    void darkForestModifierMatchesVanillaBytecode() {
        GenericMapChunkCache cache = cache();
        BiomeMap bm = biome("dark_forest");
        bm.setGrassColorModifier("dark_forest");
        int[] colormap = new int[256 * 256];
        java.util.Arrays.fill(colormap, 0x79C05A);
        // ARGB.opaque(((color & 16711422) + 2634762) >> 1)
        int expected = (((0x79C05A & 0xFEFEFE) + 0x283A2A) >> 1) | 0xFF000000;
        assertEquals(expected, cache.getGrassColor(bm, colormap, 3, 4));
    }

    @Test
    void swampModifierProducesBothVanillaTones() {
        GenericMapChunkCache cache = cache();
        BiomeMap bm = biome("swamp");
        bm.setGrassColorModifier("swamp");
        int[] colormap = new int[256 * 256];
        java.util.Arrays.fill(colormap, 0x6A7039);
        Set<Integer> tones = new HashSet<>();
        for (int x = 0; x < 200; x += 3) {
            for (int z = 0; z < 200; z += 3) {
                tones.add(cache.getGrassColor(bm, colormap, x, z));
            }
        }
        assertTrue(tones.contains(-11766212), "dark swamp patch tone missing");
        assertTrue(tones.contains(-9801671), "light swamp tone missing");
        assertTrue(tones.stream().allMatch(t -> t == -11766212 || t == -9801671),
                "swamp must only produce vanilla's two fixed tones");
    }

    @Test
    void swampModifierMatchesVanillaSimplexAtFixedCoordinates() {
        BiomeMap bm = biome("swamp");
        bm.setGrassColorModifier("swamp");
        // Ground truth captured from the real Minecraft classes: seed 2345, octave [0],
        // getValue(x*0.0225, z*0.0225) < -0.1 -> -11766212 (0xFF4C763C)
        // run against BOTH vanilla 26.2 and 26.3-snapshot-10 at these coordinates.
        int dark = -11766212, light = -9801671;
        int[][] coords = {
            {0, 0, light}, {16, 8, light}, {123, -77, light}, {-322, 512, light},
            {256, 0, dark}, {7, 13, light}, {-1280, 960, light}, {1024, -1024, light},
            {3, 4, light}, {200, 199, light}, {-45, -45, dark}, {8192, -8192, dark},
            {1, 1, light}, {-1, 1, light}, {1023, 1009, light}, {-2048, 2047, light}
        };
        for (int[] c : coords) {
            assertEquals(c[2], bm.applyGrassColorModifier(c[0], c[1], 0),
                    "swamp patch tone at (" + c[0] + "," + c[1] + ") must match vanilla");
        }
    }

    @Test
    void explicitGrassOverrideBeatsColormap() {
        GenericMapChunkCache cache = cache();
        BiomeMap bm = biome("badlands");
        bm.setGrassColorOverride(0x90814D);
        int[] colormap = new int[256 * 256];
        java.util.Arrays.fill(colormap, 0xBFB755);
        assertEquals(0x90814D, cache.getGrassColor(bm, colormap, 1, 2));
    }

    @Test
    void explicitFoliageOverrideBeatsColormap() {
        GenericMapChunkCache cache = cache();
        BiomeMap bm = biome("dark_forest");
        bm.setFoliageColorOverride(0x59AE30);
        int[] colormap = new int[256 * 256];
        java.util.Arrays.fill(colormap, 0x59C93C);
        assertEquals(0x59AE30, cache.getFoliageColor(bm, colormap, 5, 6));
    }

    private static GenericMapChunkCache cacheWithBiomeBoundary(BiomeMap swamp, BiomeMap plains) throws Exception {
        GenericMapChunkCache cache = new GenericMapChunkCache(new GenericChunkCache(4, false)) {
            @Override public boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome) { return true; }
            @Override public boolean isDoneLoading() { return true; }
            @Override public boolean isEmpty() { return false; }
            @Override public void unloadChunks() { }
            @Override public boolean isEmptySection(int sx, int sy, int sz) { return false; }
            @Override public void setHiddenFillStyle(HiddenChunkStyle style) { }
            @Override public void setVisibleRange(VisibilityLimit limit) { }
            @Override public void setHiddenRange(VisibilityLimit limit) { }
        };
        cache.setChunks(WORLD, java.util.List.of(new DynmapChunk(0, 0)));
        GenericChunkSection.Builder section = new GenericChunkSection.Builder();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                section.xzBiome(x, z, (x < 8) ? swamp : plains);
            }
        }
        GenericChunk chunk = new GenericChunk.Builder(WORLD.minY, WORLD.worldheight)
                .coords(0, 0).addSection(WORLD.sealevel >> 4, section.build()).build();
        java.lang.reflect.Field snaparrayF = GenericMapChunkCache.class.getDeclaredField("snaparray");
        snaparrayF.setAccessible(true);
        ((GenericChunk[]) snaparrayF.get(cache))[0] = chunk;
        return cache;
    }

    @Test
    void smoothGrassColorBlendsNeighbourBiomesAtEdge() throws Exception {
        // Two biomes with explicit color overrides → distinct colors at the boundary.
        BiomeMap forest = biome("forest-edge");
        forest.setGrassColorOverride(0x3A7A28);
        BiomeMap plains = biome("plains-edge");
        plains.setGrassColorOverride(0x91BD59);
        GenericMapChunkCache cache = cacheWithBiomeBoundary(forest, plains);
        int[] colormap = new int[256 * 256];
        java.util.Arrays.fill(colormap, 0xBFB755);

        int forestColor = cache.getGrassColor(forest, colormap, 7, 7);
        int plainsColor = cache.getGrassColor(plains, colormap, 8, 7);
        assertNotEquals(forestColor, plainsColor, "test setup: biomes must produce different grass colors");

        // Edge block (x=7): 6 forest + 3 plains neighbours → 3x3 average.
        MapIterator edge = cache.getIterator(7, WORLD.sealevel, 7);
        int got = edge.getSmoothGrassColorMultiplier(colormap);
        long r = 0, g = 0, b = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int c = (7 + dx < 8) ? forestColor : plainsColor;
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
        }
        int expectedAvg = (int) (((r / 9) << 16) | ((g / 9) << 8) | (b / 9));
        assertEquals(expectedAvg, got, "grass at a biome edge must use the 3x3 neighbour average for a smooth transition");
        assertNotEquals(forestColor, got, "the smooth average must differ from the block's own biome color");

        // Uniform interior (x=3): result must equal the per-block color (no blending noise).
        MapIterator interior = cache.getIterator(3, WORLD.sealevel, 3);
        assertEquals(forestColor, interior.getSmoothGrassColorMultiplier(colormap),
                "uniform biome region must keep returning the same per-block grass color");
    }
}
