package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Feeds a real Minecraft 26.3 region-file chunk through GenericMapChunkCache.parseChunkFromNBT
 * and verifies that surface grass resolves to grass_block[snowy=false] with a resolvable
 * (non-NULL) biome - the production failure mode behind snow-looking untinted grass.
 */
class Real263ChunkParseTest {

    private static final String REGION = System.getenv("AIRMAP_REAL_REGION_FILE");

    // ---- Minimal NBT model ----
    record NbtList(List<Object> values, byte elementType) {}
    record NbtString(String value) {}

    private static Object readTagPayload(DataInputStream in, int type) throws Exception {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int len = in.readInt();
                byte[] b = new byte[len];
                in.readFully(b);
                return b;
            }
            case 8: return new NbtString(in.readUTF());
            case 9: {
                int et = in.readByte();
                int len = in.readInt();
                List<Object> vals = new ArrayList<>(len);
                for (int i = 0; i < len; i++) vals.add(readTagPayload(in, et));
                return new NbtList(vals, (byte) et);
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) break;
                    String name = in.readUTF();
                    map.put(name, readTagPayload(in, t));
                }
                return map;
            }
            case 11: {
                int len = in.readInt();
                int[] a = new int[len];
                for (int i = 0; i < len; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int len = in.readInt();
                long[] a = new long[len];
                for (int i = 0; i < len; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IllegalStateException("unsupported tag type " + type);
        }
    }

    private static Map<String, Object> readRoot(byte[] data) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int t = in.readByte();
        in.readUTF(); // root name
        if (t != 10) throw new IllegalStateException("root not compound");
        return (Map<String, Object>) readTagPayload(in, 10);
    }

    // ---- GenericNBTCompound over the minimal model (mirrors fabric NBT wrapper semantics) ----
    static class MapCompound implements GenericNBTCompound {
        final Map<String, Object> map;
        final String scalarNameValue;
        MapCompound(Map<String, Object> map) { this(map, null); }
        MapCompound(Map<String, Object> map, String scalarNameValue) {
            this.map = map;
            this.scalarNameValue = scalarNameValue;
        }
        @Override public Set<String> getAllKeys() { return map.keySet(); }
        @Override public boolean contains(String s) { return map.containsKey(s); }
        @Override public boolean contains(String s, int i) { return map.containsKey(s); }
        private Number num(String s) { Object o = map.get(s); return (o instanceof Number n) ? n : 0; }
        @Override public byte getByte(String s) { return num(s).byteValue(); }
        @Override public short getShort(String s) { return num(s).shortValue(); }
        @Override public int getInt(String s) { return num(s).intValue(); }
        @Override public long getLong(String s) { return num(s).longValue(); }
        @Override public float getFloat(String s) { return num(s).floatValue(); }
        @Override public double getDouble(String s) { return num(s).doubleValue(); }
        private String str(String s) {
            Object o = map.get(s);
            return (o instanceof NbtString st) ? st.value() : "";
        }
        @Override public String getString(String s) {
            if ("Name".equals(s)) {
                if (scalarNameValue != null) return scalarNameValue;
                String v = str("id");
                if (!v.isEmpty()) return v;
                return str("");
            }
            return str(s);
        }
        @Override public byte[] getByteArray(String s) { Object o = map.get(s); return (o instanceof byte[] b) ? b : new byte[0]; }
        @Override public int[] getIntArray(String s) { Object o = map.get(s); return (o instanceof int[] a) ? a : new int[0]; }
        @Override public long[] getLongArray(String s) { Object o = map.get(s); return (o instanceof long[] a) ? a : new long[0]; }
        @Override public GenericNBTCompound getCompound(String s) {
            Object o = map.get(s);
            return (o instanceof Map<?, ?> m) ? new MapCompound((Map<String, Object>) m) : new MapCompound(Map.of());
        }
        @Override public GenericNBTList getList(String s, int i) {
            Object o = map.get(s);
            return (o instanceof NbtList l) ? new MapList(l) : new MapList(new NbtList(List.of(), (byte) 0));
        }
        @Override public boolean getBoolean(String s) { Object o = map.get(s); return (o instanceof Byte b) && b != 0; }
        @Override public String getAsString(String s) { return getString(s); }
        @Override public GenericBitStorage makeBitStorage(int bits, int count, long[] data) {
            return new SimplePacked(bits, count, data);
        }
    }

    static class MapList implements GenericNBTList {
        private final NbtList l;
        MapList(NbtList l) { this.l = l; }
        @Override public int size() { return l.values().size(); }
        @Override public String getString(int idx) {
            Object o = l.values().get(idx);
            return (o instanceof NbtString s) ? s.value() : "";
        }
        @Override public GenericNBTCompound getCompound(int idx) {
            Object o = l.values().get(idx);
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> mm = (Map<String, Object>) m;
                // Mirror fabric wrapper: bare-string-ish entries expose their single value under Name
                String scalar = null;
                if (!mm.containsKey("id") && !mm.containsKey("Name") && mm.size() == 1) {
                    Object v = mm.values().iterator().next();
                    if (v instanceof NbtString s) scalar = s.value();
                }
                return new MapCompound(mm, scalar);
            }
            if (o instanceof NbtString s) {
                return new MapCompound(Map.of(), s.value());
            }
            return new MapCompound(Map.of());
        }
    }

    /** Packed-bit reader matching vanilla SimpleBitStorage layout (values-per-long rounding with padding). */
    static class SimplePacked implements GenericBitStorage {
        private final int bits, valuesPerLong, mask;
        private final long[] data;
        SimplePacked(int bits, int count, long[] data) {
            this.bits = bits;
            this.valuesPerLong = 64 / bits;
            this.mask = (1 << bits) - 1;
            this.data = data;
        }
        @Override public int get(int idx) {
            long l = data[idx / valuesPerLong];
            int off = (idx % valuesPerLong) * bits;
            return (int) ((l >> off) & mask);
        }
    }

    private static byte[] readRegionChunk(String path, int cx, int cz) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            int idx = ((cx & 31) + (cz & 31) * 32) << 2;
            raf.seek(idx);
            int ent = raf.readInt();
            int off = ent >>> 8;
            if (off == 0) return null;
            raf.seek(off * 4096L);
            int len = raf.readInt();
            int comp = raf.readByte();
            byte[] raw = new byte[len - 1];
            raf.readFully(raw);
            java.io.InputStream src = (comp == 2)
                    ? new java.util.zip.InflaterInputStream(new ByteArrayInputStream(raw))
                    : new java.util.zip.GZIPInputStream(new ByteArrayInputStream(raw));
            return src.readAllBytes();
        }
    }

    @Test
    void realChunkKeepsGrassUntintedStateAndResolvableBiomes() throws Exception {
        Assumptions.assumeTrue(REGION != null && !REGION.isBlank(), "AIRMAP_REAL_REGION_FILE not set");
        ByteBuffer buf = ByteBuffer.wrap(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(REGION)));
        assertTrue(buf.capacity() > 8192, "region too small");

        DynmapBlockState grassDefault = new DynmapBlockState.Builder().setBlockName("minecraft:grass_block")
                .setStateName("snowy=false").setAttenuatesLight(15).build();
        new DynmapBlockState.Builder().setBaseState(grassDefault).setStateIndex(1)
                .setBlockName("minecraft:grass_block").setStateName("snowy=true")
                .setAttenuatesLight(15).build();

        GenericMapChunkCache cache = new GenericMapChunkCache(new GenericChunkCache(16, false)) {
            @Override public boolean isDoneLoading() { return true; }
            @Override public boolean isEmpty() { return false; }
            @Override public void unloadChunks() {}
            @Override public boolean isEmptySection(int sx, int sy, int sz) { return false; }
            @Override public org.dynmap.utils.MapIterator getIterator(int x, int y, int z) { return null; }
            @Override public void setHiddenFillStyle(org.dynmap.utils.MapChunkCache.HiddenChunkStyle style) {}
            @Override public void setVisibleRange(org.dynmap.utils.VisibilityLimit limit) {}
            @Override public void setHiddenRange(org.dynmap.utils.VisibilityLimit limit) {}
            @Override public org.dynmap.DynmapWorld getWorld() { return null; }
        };
        java.lang.reflect.Field dwf = GenericMapChunkCache.class.getDeclaredField("dw");
        dwf.setAccessible(true);
        dwf.set(cache, new org.dynmap.DynmapWorld("test", 384, 63, -64) {
            @Override public boolean isLoaded() { return true; }
            @Override public boolean isNether() { return false; }
            @Override public org.dynmap.DynmapLocation getSpawnLocation() { return null; }
            @Override public long getTime() { return 0; }
            @Override public boolean hasStorm() { return false; }
            @Override public boolean isThundering() { return false; }
            @Override public void setWorldUnloaded() {}
            @Override public int getLightLevel(int x, int y, int z) { return 15; }
            @Override public int getHighestBlockYAt(int x, int z) { return 64; }
            @Override public boolean canGetSkyLightLevel() { return false; }
            @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
            @Override public String getEnvironment() { return "normal"; }
            @Override public org.dynmap.utils.MapChunkCache getChunkCache(java.util.List<org.dynmap.DynmapChunk> chunks) { return null; }
        });

        Set<String> unresolvedBiomes = new HashSet<>();
        Map<String, Integer> resolvedBiomes = new HashMap<>();
        boolean foundGrass = false;
        String lastStatus = null;
        int chunksParsed = 0;

        outer:
        for (int cz = 0; cz < 32; cz++) {
            for (int cx = 0; cx < 32; cx++) {
                byte[] data = readRegionChunk(REGION, cx, cz);
                if (data == null) continue;
                Map<String, Object> root;
                try { root = readRoot(data); } catch (Exception e) { continue; }
                Object stv = root.get("Status");
                String status = (stv instanceof NbtString s) ? s.value() : String.valueOf(stv);
                lastStatus = status;
                if (!status.endsWith(":full")) continue;
                GenericChunk chunk = cache.parseChunkFromNBT(new MapCompound(root));
                assertNotNull(chunk, "chunk should parse");
                chunksParsed++;

                // Collect biome palette names exactly as the parser sees them
                Object secs = root.get("sections");
                if (secs instanceof NbtList sl) {
                    for (Object so : sl.values()) {
                        Map<String, Object> sec = (Map<String, Object>) so;
                        Object bio = sec.get("biomes");
                        if (!(bio instanceof Map<?, ?>)) continue;
                        Object pal = ((Map<String, Object>) bio).get("palette");
                        if (!(pal instanceof NbtList pl)) continue;
                        for (Object po : pl.values()) {
                            String rl = (po instanceof NbtString s) ? s.value() : "";
                            if (rl.isEmpty()) continue;
                            BiomeMap bm = BiomeMap.byBiomeResourceLocation(rl);
                            if (bm == null || bm == BiomeMap.NULL || bm.isDefault() == false && bm.toString().isEmpty()) {
                                unresolvedBiomes.add(rl);
                            } else if (bm != BiomeMap.NULL) {
                                resolvedBiomes.merge(rl, 1, Integer::sum);
                            }
                        }
                    }
                }

                // Locate a surface grass_block and verify its state + biome
                for (int y = chunk.cy_min * 16; y < (chunk.cy_min + chunk.sectionCnt) * 16; y++) {
                    for (int x = 0; x < 16 && !foundGrass; x++) {
                        for (int z = 0; z < 16 && !foundGrass; z++) {
                            DynmapBlockState bs = chunk.getBlockType(x, y, z);
                            if (bs != null && "minecraft:grass_block".equals(bs.blockName)) {
                                foundGrass = true;
                                System.out.println("[real-chunk] grass at (" + x + "," + y + "," + z + ") state="
                                        + bs + " stateName=" + bs.stateName
                                        + " biome=" + chunk.getBiome(x, y, z));
                                assertNotEquals("snowy=true", bs.stateName,
                                        "surface grass must not parse as snowy variant");
                                BiomeMap bm = chunk.getBiome(x, y, z);
                                assertNotNull(bm);
                                assertNotEquals(BiomeMap.NULL, bm, "biome must resolve, else grass renders untinted/gray");
                            }
                        }
                    }
                    if (foundGrass) break;
                }
                if (chunksParsed >= 3) break outer;
            }
        }
        System.out.println("[real-chunk] chunksParsed=" + chunksParsed + " lastStatus=" + lastStatus);
        System.out.println("[real-chunk] resolvedBiomes=" + resolvedBiomes);
        System.out.println("[real-chunk] unresolvedBiomes=" + unresolvedBiomes);
        assertTrue(chunksParsed > 0, "no full chunks found in region");
        assertTrue(unresolvedBiomes.isEmpty(), "unresolved biome resource locations: " + unresolvedBiomes);
    }
}
