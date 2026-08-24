package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.dynmap.DynmapWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.utils.MapChunkCache;
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
}
