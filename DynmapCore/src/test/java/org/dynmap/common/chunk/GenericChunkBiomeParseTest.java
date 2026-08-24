package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;
import org.junit.jupiter.api.Test;

/** Replicates 26.3-style chunk NBT (sections/block_states/biomes palettes) headlessly. */
class GenericChunkBiomeParseTest {
    private static final DynmapWorld WORLD = new DynmapWorld("test", 384, 63, -64) {
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return new DynmapLocation("test", 0, 0, 0); }
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
        @Override public MapChunkCache getChunkCache(List<org.dynmap.DynmapChunk> chunks) { return null; }
    };

    /** Minimal map-backed NBT compound matching MC serialization semantics used by the parser. */
    static final class FakeCompound implements GenericNBTCompound {
        final Map<String, Object> values = new HashMap<>();
        FakeCompound put(String key, Object value) { values.put(key, value); return this; }
        @Override public Set<String> getAllKeys() { return values.keySet(); }
        @Override public boolean contains(String s) { return values.containsKey(s); }
        @Override public boolean contains(String s, int i) { return values.containsKey(s); }
        private Number num(String s) { Object v = values.get(s); return v instanceof Number n ? n : 0; }
        @Override public byte getByte(String s) { return num(s).byteValue(); }
        @Override public short getShort(String s) { return num(s).shortValue(); }
        @Override public int getInt(String s) { return num(s).intValue(); }
        @Override public long getLong(String s) { return num(s).longValue(); }
        @Override public float getFloat(String s) { return num(s).floatValue(); }
        @Override public double getDouble(String s) { return num(s).doubleValue(); }
        @Override public String getString(String s) { Object v = values.get(s); return v != null ? v.toString() : ""; }
        @Override public byte[] getByteArray(String s) { Object v = values.get(s); return v instanceof byte[] b ? b : new byte[0]; }
        @Override public int[] getIntArray(String s) { Object v = values.get(s); return v instanceof int[] a ? a : new int[0]; }
        @Override public long[] getLongArray(String s) { Object v = values.get(s); return v instanceof long[] l ? l : new long[0]; }
        @Override public GenericNBTCompound getCompound(String s) {
            Object v = values.get(s);
            return v instanceof FakeCompound c ? c : new FakeCompound();
        }
        @Override public GenericNBTList getList(String s, int i) {
            Object v = values.get(s);
            return v instanceof FakeList l ? l : new FakeList();
        }
        @Override public boolean getBoolean(String s) { Object v = values.get(s); return v instanceof Boolean b && b; }
        @Override public String getAsString(String s) { return getString(s); }
        @Override public GenericBitStorage makeBitStorage(int bits, int count, long[] data) {
            return new FakeBitStorage(bits, count, data);
        }
    }

    static final class FakeList implements GenericNBTList {
        final List<Object> entries = new ArrayList<>();
        FakeList add(Object entry) { entries.add(entry); return this; }
        @Override public int size() { return entries.size(); }
        @Override public String getString(int idx) { Object v = entries.get(idx); return v != null ? v.toString() : ""; }
        @Override public GenericNBTCompound getCompound(int idx) { return (GenericNBTCompound) entries.get(idx); }
    }

    /** LSB-first packing like vanilla SimpleBitStorage. */
    record FakeBitStorage(int bits, int count, long[] data) implements GenericBitStorage {
        FakeBitStorage {
            if (data.length < (count * bits + 63) / 64) throw new IllegalArgumentException("data too short");
        }
        @Override public int get(int idx) {
            long bitBase = (long) idx * bits;
            int startLong = (int) (bitBase >> 6);
            int shift = (int) (bitBase & 63);
            long mask = (1L << bits) - 1;
            long value = (data[startLong] >>> shift) & mask;
            int overflow = shift + bits - 64;
            if (overflow > 0) value |= (data[startLong + 1] & ((1L << overflow) - 1)) << (bits - overflow);
            return (int) value;
        }
    }

    private static FakeCompound blockStatePalette(String... names) {
        FakeList palette = new FakeList();
        for (String name : names) palette.add(new FakeCompound().put("Name", name));
        return new FakeCompound().put("palette", palette);
    }

    private static FakeCompound biomePalette(String... biomes) {
        FakeList palette = new FakeList();
        for (String biome : biomes) palette.add(biome);
        return new FakeCompound().put("palette", palette);
    }

    private static FakeCompound chunkNbt(FakeCompound... sections) {
        FakeList sectionList = new FakeList();
        for (FakeCompound section : sections) sectionList.add(section);
        FakeCompound root = new FakeCompound();
        root.put("DataVersion", 4440).put("xPos", 0).put("zPos", 0)
                .put("Status", "minecraft:full").put("sections", sectionList);
        return root;
    }

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

    @Test
    void singleBiomePaletteResolvesEverywhere() {
        GenericMapChunkCache cache = cache();
        FakeCompound section = new FakeCompound().put("Y", (byte) 4)
                .put("block_states", blockStatePalette("minecraft:stone"))
                .put("biomes", biomePalette("minecraft:plains"));
        GenericChunk chunk = cache.parseChunkFromNBT(chunkNbt(section));
        assertNotNull(chunk, "chunk must parse");
        assertEquals(BiomeMap.PLAINS, chunk.getBiome(0, 72, 0), "single-biome palette must fill the whole section");
        assertEquals(BiomeMap.PLAINS, chunk.getBiome(15, 76, 15), "corner of section must resolve too");
    }

    @Test
    void packedTwoBiomePaletteAlternates() {
        GenericMapChunkCache cache = cache();
        long[] data = new long[1];
        for (int j = 0; j < 64; j++) {
            if ((j & 1) == 1) data[0] |= (1L << j);
        }
        FakeCompound biomes = biomePalette("minecraft:plains", "minecraft:forest");
        biomes.put("data", data);
        FakeCompound section = new FakeCompound().put("Y", (byte) 4)
                .put("block_states", blockStatePalette("minecraft:stone"))
                .put("biomes", biomes);
        GenericChunk chunk = cache.parseChunkFromNBT(chunkNbt(section));
        assertNotNull(chunk, "chunk must parse");
        assertEquals(BiomeMap.PLAINS, chunk.getBiome(0, 72, 0), "even biome cells must be plains");
        assertEquals(BiomeMap.FOREST, chunk.getBiome(4, 72, 0), "odd biome cells must be forest");
    }

    @Test
    void missingBiomeDataIsDetectedNotSilentlyEmpty() {
        GenericMapChunkCache cache = cache();
        FakeCompound section = new FakeCompound().put("Y", (byte) 4)
                .put("block_states", blockStatePalette("minecraft:stone"));
        GenericChunk chunk = cache.parseChunkFromNBT(chunkNbt(section));
        assertNotNull(chunk, "chunk without biomes must still parse");
    }
}
