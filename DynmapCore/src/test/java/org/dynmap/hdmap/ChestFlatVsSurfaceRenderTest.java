package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.VisibilityLimit;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * DECISIVE: render a real synthetic world (grass + ONE chest) through the REAL
 * raytrace pipeline (IsoHDPerspective.OurPerspectiveState.raytrace + stdtexture
 * shader, native tiles) for the two maps used in the realtest:
 *   FLAT    = iso_S_90_lowres (azimuth=180, inclination=90, scale=4)
 *   SURFACE = iso_SE_30_hires (azimuth=135, inclination=30, scale=16)
 * and count pixels whose final opaque color is produced by the chest block.
 * Goal: locate the FIRST stage where the chest is lost in FLAT but NOT in
 * SURFACE (positive control).
 */
class ChestFlatVsSurfaceRenderTest {
    private static DynmapCore core;
    private static TexturePack tp;
    private static TexturePackHDShader shader;

    private static final DynmapWorld WORLD = new DynmapWorld("diag", 384, 63, -64) {
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return new DynmapLocation("diag", 0, 0, 0); }
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
        @Override public String getAsString(String s) { return getString(s); }
        @Override public boolean getBoolean(String s) { Object v = values.get(s); return v instanceof Boolean b && b; }
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

    private static long[] packStates(int bits, int[] indices) {
        long[] data = new long[(indices.length * bits + 63) / 64];
        for (int i = 0; i < indices.length; i++) {
            long bitBase = (long) i * bits;
            int startLong = (int) (bitBase >> 6);
            int shift = (int) (bitBase & 63);
            data[startLong] |= ((long) indices[i]) << shift;
            int overflow = shift + bits - 64;
            if (overflow > 0) data[startLong + 1] |= indices[i] >>> (64 - shift) & ((1L << overflow) - 1);
        }
        return data;
    }

    /** Section Y=4: grass at localY=6 (world 70), dirt at localY=5, TARGET at localY=7 at chestLocalX/Z. */
    private static FakeCompound surfaceSection(String targetName, int targetLocalX, int targetLocalZ) {
        FakeList palette = new FakeList();
        palette.add(new FakeCompound().put("Name", "minecraft:air"));
        palette.add(new FakeCompound().put("Name", "minecraft:dirt"));
        palette.add(new FakeCompound().put("Name", "minecraft:grass_block"));
        palette.add(new FakeCompound().put("Name", targetName));
        int[] idx = new int[16 * 16 * 16];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int i = y * 256 + z * 16 + x;
                    if (y == 7 && x == targetLocalX && z == targetLocalZ) idx[i] = 3;
                    else idx[i] = (y == 6) ? 2 : ((y == 5) ? 1 : 0);
                }
        FakeCompound bs = new FakeCompound().put("palette", palette).put("data", packStates(4, idx));
        FakeCompound biomes = new FakeCompound().put("palette", new FakeList().add(new FakeCompound().put("Name", "minecraft:plains")));
        return new FakeCompound().put("Y", (byte) 4).put("block_states", bs).put("biomes", biomes);
    }

    private static FakeCompound chunkNbt(int cx, int cz, String targetName, int targetLocalX, int targetLocalZ) {
        FakeCompound root = new FakeCompound();
        root.put("DataVersion", 4440).put("xPos", cx).put("zPos", cz)
                .put("Status", "minecraft:full")
                .put("sections", new FakeList().add(surfaceSection(targetName, targetLocalX, targetLocalZ)));
        return root;
    }

    private static GenericMapChunkCache buildCache(String targetName) throws Exception {
        int targetLocalX = 8, targetLocalZ = 8;
        List<DynmapChunk> chunks = new ArrayList<>();
        for (int cz = -1; cz <= 1; cz++)
            for (int cx = -1; cx <= 1; cx++)
                chunks.add(new DynmapChunk(cx, cz));
        GenericMapChunkCache cache = new GenericMapChunkCache(new GenericChunkCache(4, false)) {
            @Override public boolean setChunkDataTypes(boolean b, boolean bi, boolean h, boolean r) { return true; }
            @Override public boolean isDoneLoading() { return true; }
            @Override public boolean isEmpty() { return false; }
            @Override public void unloadChunks() { }
            @Override public boolean isEmptySection(int sx, int sy, int sz) { return false; }
            @Override public void setHiddenFillStyle(HiddenChunkStyle style) { }
            @Override public void setVisibleRange(VisibilityLimit limit) { }
            @Override public void setHiddenRange(VisibilityLimit limit) { }
        };
        cache.setChunks(WORLD, chunks);
        Field snaparrayF = GenericMapChunkCache.class.getDeclaredField("snaparray");
        snaparrayF.setAccessible(true);
        GenericChunk[] snaps = (GenericChunk[]) snaparrayF.get(cache);
        int xMin = Integer.MAX_VALUE, zMin = Integer.MAX_VALUE;
        for (DynmapChunk c : chunks) { xMin = Math.min(xMin, c.x); zMin = Math.min(zMin, c.z); }
        int xDim = (int) chunks.stream().map(c -> c.x).distinct().count();
        for (DynmapChunk c : chunks) {
            GenericChunk parsed = cache.parseChunkFromNBT(chunkNbt(c.x, c.z, targetName, targetLocalX, targetLocalZ));
            assertNotNull(parsed, "chunk " + c.x + "," + c.z + " must parse");
            snaps[(c.x - xMin) + (c.z - zMin) * xDim] = parsed;
        }
        return cache;
    }

    static final class StubServer extends DynmapServerInterface {
        @Override
        public InputStream openResource(String modid, String rname) {
            return ChestFlatVsSurfaceRenderTest.class.getClassLoader()
                    .getResourceAsStream("texturepacks/standard/" + rname);
        }
        @Override public void scheduleServerTask(Runnable run, long delay) { }
        @Override public <T> java.util.concurrent.Future<T> callSyncMethod(java.util.concurrent.Callable<T> task) { return null; }
        @Override public org.dynmap.common.DynmapPlayer[] getOnlinePlayers() { return new org.dynmap.common.DynmapPlayer[0]; }
        @Override public void reload() { }
        @Override public org.dynmap.common.DynmapPlayer getPlayer(String name) { return null; }
        @Override public org.dynmap.common.DynmapPlayer getOfflinePlayer(String name) { return null; }
        @Override public Set<String> getIPBans() { return Set.of(); }
        @Override public String getServerName() { return "diag"; }
        @Override public boolean isPlayerBanned(String pid) { return false; }
        @Override public String stripChatColor(String s) { return s; }
        @Override public boolean requestEventNotification(org.dynmap.common.DynmapListenerManager.EventType type) { return false; }
        @Override public void broadcastMessage(String msg) { }
        @Override public double getCacheHitRate() { return 0; }
        @Override public void resetCacheStats() { }
        @Override public DynmapWorld getWorldByName(String wname) { return WORLD; }
        @Override public boolean checkPlayerPermission(String player, String perm) { return false; }
        @Override public MapChunkCache createMapChunkCache(DynmapWorld w, List<org.dynmap.DynmapChunk> chunks,
                boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) { return null; }
        @Override public int getMaxPlayers() { return 0; }
        @Override public int getCurrentPlayers() { return 0; }
        @Override public int isSignAt(String wname, int x, int y, int z) { return -1; }
        @Override public String getServerIP() { return null; }
    }

    private static void boot() throws Exception {
        String cacheDir = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cacheDir != null && !cacheDir.isBlank(), "client test cache not requested");
        core = new DynmapCore();
        core.setDataFolder(java.nio.file.Path.of(cacheDir).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());
        core.setServer(new StubServer());

        if (DynmapBlockState.getBaseStateByName("minecraft:grass_block") == DynmapBlockState.AIR) {
            new DynmapBlockState.Builder().setBlockName("minecraft:dirt").setAttenuatesLight(15).build();
            DynmapBlockState grassBase = new DynmapBlockState.Builder().setBlockName("minecraft:grass_block")
                    .setStateName("snowy=false").setAttenuatesLight(15).build();
            new DynmapBlockState.Builder().setBaseState(grassBase).setStateIndex(1)
                    .setBlockName("minecraft:grass_block").setStateName("snowy=true").setAttenuatesLight(15).build();
            new DynmapBlockState.Builder().setBlockName("minecraft:stone").setAttenuatesLight(15).build();
            new DynmapBlockState.Builder().setBlockName("minecraft:glass").build();
            new DynmapBlockState.Builder().setBlockName("minecraft:oak_leaves").setLeaves().build();
            new DynmapBlockState.Builder().setBlockName("minecraft:chest")
                    .setStateName("facing=north,type=single,waterlogged=false").build();
            new DynmapBlockState.Builder().setBlockName("minecraft:shulker_box")
                    .setStateName("facing=up").build();
            new DynmapBlockState.Builder().setBlockName("minecraft:copper_golem_statue")
                    .setStateName("copper_golem_pose=standing,facing=north").build();
        }

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        sun.misc.Unsafe unsafe = unsafe();
        MapManager mm = (MapManager) unsafe.allocateInstance(MapManager.class);
        Field mmcF = MapManager.class.getDeclaredField("core");
        mmcF.setAccessible(true);
        mmcF.set(mm, core);
        Field mapmanF = MapManager.class.getDeclaredField("mapman");
        mapmanF.setAccessible(true);
        mapmanF.set(null, mm);

        ConfigurationNode shaderCfg = new ConfigurationNode();
        shaderCfg.put("texturepack", "standard");
        shaderCfg.put("name", "diag");
        shaderCfg.put("better-grass", Boolean.FALSE);
        shader = new TexturePackHDShader(core, shaderCfg);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static HDLighting passThroughLighting() {
        return new HDLighting() {
            @Override public String getName() { return "diag"; }
            @Override public void applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
                outcolor[0].setColor(incolor);
            }
            @Override public boolean isBiomeDataNeeded() { return true; }
            @Override public boolean isRawBiomeDataNeeded() { return false; }
            @Override public boolean isHightestBlockYDataNeeded() { return false; }
            @Override public boolean isBlockTypeDataNeeded() { return true; }
            @Override public boolean isSkyLightLevelNeeded() { return false; }
            @Override public boolean isEmittedLightLevelNeeded() { return false; }
            @Override public boolean isNightAndDayEnabled() { return false; }
            @Override public int[] getBrightnessTable(DynmapWorld w) { return null; }
            @Override public void addClientConfiguration(JsonObject o) { }
        };
    }

    private static TexturePackHDShader.ShaderState makeShaderState(GenericMapChunkCache cache, MapIterator mapiter) throws Exception {
        sun.misc.Unsafe unsafe = unsafe();
        HDMap hdmap = (HDMap) unsafe.allocateInstance(HDMap.class);
        Field lightF = HDMap.class.getDeclaredField("lighting");
        lightF.setAccessible(true);
        lightF.set(hdmap, passThroughLighting());

        Constructor<TexturePackHDShader.ShaderState> ctor =
                TexturePackHDShader.ShaderState.class.getDeclaredConstructor(
                        TexturePackHDShader.class, MapIterator.class, HDMap.class, MapChunkCache.class, int.class);
        ctor.setAccessible(true);
        TexturePackHDShader.ShaderState ss = ctor.newInstance(shader, mapiter, hdmap, cache, Integer.valueOf(4));

        Field tpF = TexturePackHDShader.class.getDeclaredField("tp");
        tpF.setAccessible(true);
        tp = (TexturePack) tpF.get(shader);

        Field stpF = TexturePackHDShader.ShaderState.class.getDeclaredField("scaledtp");
        stpF.setAccessible(true);
        stpF.set(ss, tp);
        return ss;
    }

    private static IsoHDPerspective buildPerspective(String name, double azimuth, double inclination, int scale) {
        ConfigurationNode cfg = new ConfigurationNode();
        cfg.put("name", name);
        cfg.put("azimuth", Double.valueOf(azimuth));
        cfg.put("inclination", Double.valueOf(inclination));
        cfg.put("scale", Integer.valueOf(scale));
        cfg.put("maximumheight", Integer.valueOf(96));
        cfg.put("minimumheight", Integer.valueOf(-64));
        return new IsoHDPerspective(core, cfg);
    }

    static final class Hit {
        final DynmapBlockState blk; final int patchid; final int txtId; final double u, v; final BlockStep step; final int alpha;
        final int px, py, bx, by, bz;
        final double tx, tz;
        Hit(DynmapBlockState blk, int patchid, int txtId, double u, double v, BlockStep step, int alpha,
            int px, int py, int bx, int by, int bz, double tx, double tz) {
            this.blk = blk; this.patchid = patchid; this.txtId = txtId; this.u = u; this.v = v; this.step = step; this.alpha = alpha;
            this.px = px; this.py = py; this.bx = bx; this.by = by; this.bz = bz;
            this.tx = tx; this.tz = tz;
        }
    }

    private static int resolveTextId(DynmapBlockState blk, int patchid, BlockStep laststep) {
        int faceindex = (patchid >= 0) ? patchid : laststep.ordinal();
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(blk);
        return (map != null && faceindex < map.faces.length) ? map.faces[faceindex] : -1;
    }

    private static TexturePack samplingPack(TexturePackHDShader.ShaderState ss) {
        try {
            Field stpF = TexturePackHDShader.ShaderState.class.getDeclaredField("scaledtp");
            stpF.setAccessible(true);
            TexturePack p = (TexturePack) stpF.get(ss);
            return (p != null) ? p : tp;
        } catch (Exception e) {
            return tp;
        }
    }

    static final class RecordingState implements HDShaderState {
        final TexturePackHDShader.ShaderState ss;
        final TexturePack samplePack;
        final List<Hit> hits = new ArrayList<>();
        int px, py; double tx, tz;
        RecordingState(TexturePackHDShader.ShaderState ss) { this.ss = ss; this.samplePack = samplingPack(ss); }
        @Override public boolean processBlock(HDPerspectiveState ps) {
            if (ps.getTextureIndex() >= 0 && !ps.getBlockState().isAir()) {
                int tid = resolveTextId(ps.getBlockState(), ps.getTextureIndex(), ps.getLastBlockStep());
                int alpha = -1;
                try {
                    Color c = new Color();
                    samplePack.readColor(ps, ps.getMapIterator(), c, ps.getBlockState(), ps.getBlockState(), ss);
                    alpha = (c.getARGB() >>> 24) & 0xFF;
                } catch (Exception e) {
                    alpha = -2;
                }
                hits.add(new Hit(ps.getBlockState(), ps.getTextureIndex(), tid, ps.getPatchU(), ps.getPatchV(),
                        ps.getLastBlockStep(), alpha, this.px, this.py,
                        ps.getMapIterator().getX(), ps.getMapIterator().getY(), ps.getMapIterator().getZ(),
                        tx, tz));
            }
            return ss.processBlock(ps);
        }
        @Override public HDShader getShader() { return ss.getShader(); }
        @Override public HDMap getMap() { return ss.getMap(); }
        @Override public HDLighting getLighting() { return ss.getLighting(); }
        @Override public void reset(HDPerspectiveState ps) { ss.reset(ps); }
        @Override public void rayFinished(HDPerspectiveState ps) { ss.rayFinished(ps); }
        @Override public void getRayColor(Color c, int index) { ss.getRayColor(c, index); }
        @Override public void cleanup() { ss.cleanup(); }
        @Override public DynLongHashMap getCTMTextureCache() { return ss.getCTMTextureCache(); }
        @Override public int[] getLightingTable() { return ss.getLightingTable(); }
        @Override public void setLastBlockState(DynmapBlockState bs) { ss.setLastBlockState(bs); }
    }

    private static String alphaGrid(int[] argb) {
        int size = (int) Math.round(Math.sqrt(argb.length));
        StringBuilder sb = new StringBuilder(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sb.append(String.format("%3d ", (argb[y * size + x] >>> 24) & 0xFF));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void dumpFaceTile(TexturePack pack, int texid, String label) {
        System.out.println("  tile " + label + " texid=" + texid +
                " size=" + (int) Math.round(Math.sqrt(pack.getTileARGB(texid).length)));
        System.out.println(alphaGrid(pack.getTileARGB(texid)));
    }

    record TraceStats(int rendered, int targetVisited, int targetHitButTransparent, int targetBecameLastOpaque) {
        String csv() {
            return "(" + rendered + "," + targetVisited + "," + targetHitButTransparent + "," + targetBecameLastOpaque + ")";
        }
    }

    private static TraceStats traceTile(IsoHDPerspective persp, GenericMapChunkCache cache, String label, int window,
            boolean useResampled, DynmapBlockState target) throws Exception {
        MapIterator mapiter = cache.getIterator(8, 70, 8);
        TexturePackHDShader.ShaderState ss = makeShaderState(cache, mapiter);
        if (useResampled) {
            Field stpF = TexturePackHDShader.ShaderState.class.getDeclaredField("scaledtp");
            stpF.setAccessible(true);
            Field pf = IsoHDPerspective.class.getDeclaredField("basemodscale");
            pf.setAccessible(true);
            stpF.set(ss, tp.resampleTexturePack(pf.getInt(persp)));
        }
        RecordingState rec = new RecordingState(ss);

        Class<?> psClass = null;
        for (Class<?> c : IsoHDPerspective.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("OurPerspectiveState")) psClass = c;
        }
        assertNotNull(psClass, "OurPerspectiveState must exist");
        Constructor<?> psCtor = psClass.getDeclaredConstructor(IsoHDPerspective.class, MapIterator.class, boolean.class, int.class);
        psCtor.setAccessible(true);
        Object ps = psCtor.newInstance(persp, mapiter, Boolean.FALSE, Integer.valueOf(0));

        Field topF = psClass.getDeclaredField("top"); topF.setAccessible(true);
        Field bottomF = psClass.getDeclaredField("bottom"); bottomF.setAccessible(true);
        Field dirF = psClass.getDeclaredField("direction"); dirF.setAccessible(true);
        Field pxF = psClass.getDeclaredField("px"); pxF.setAccessible(true);
        Field pyF = psClass.getDeclaredField("py"); pyF.setAccessible(true);
        Field mtxF = IsoHDPerspective.class.getDeclaredField("map_to_world"); mtxF.setAccessible(true);
        org.dynmap.utils.Matrix3D m2w = (org.dynmap.utils.Matrix3D) mtxF.get(persp);
        Field w2mF = IsoHDPerspective.class.getDeclaredField("world_to_map"); w2mF.setAccessible(true);
        org.dynmap.utils.Matrix3D w2m = (org.dynmap.utils.Matrix3D) w2mF.get(persp);

        double height = 96.0, miny = -64.0;

        double cx = 0, cy = 0;
        {
            Vector3D proj = new Vector3D(8.5, 70.5, 8.5);
            w2m.transform(proj);
            cx = proj.x; cy = proj.y;
            System.out.println("  [projection] world(8.5,70.5,8.5) -> map(" + String.format("%.1f,%.1f", cx, cy) + ")");
        }
        double xbase = Math.floor(cx - window / 2.0), ybase = Math.floor(cy - window / 2.0);
        int tileSize = window;

        Vector3D dirv = new Vector3D(0.0, 0.0, (miny - 0.5) - (height + 0.5));
        m2w.transform(dirv);
        dirF.set(ps, dirv);
        Vector3D xstep = new Vector3D(1.0, 0.0, 0.0); m2w.transform(xstep);
        Vector3D ystep = new Vector3D(0.0, 1.0, 0.0); m2w.transform(ystep);
        Vector3D zb = new Vector3D(0.0, 0.0, height + 0.5); m2w.transform(zb);

        Method raytrace = psClass.getDeclaredMethod("raytrace", MapChunkCache.class, HDShaderState[].class, boolean[].class);
        raytrace.setAccessible(true);
        Method resetM = HDShaderState.class.getMethod("reset", HDPerspectiveState.class);
        Method rayFinished = HDShaderState.class.getMethod("rayFinished", HDPerspectiveState.class);
        Method getRayColor = HDShaderState.class.getMethod("getRayColor", Color.class, int.class);

        int targetVisited = 0;
        int targetBecameLastOpaque = 0;
        int targetHitButTransparent = 0;
        int rendered = 0;
        Color rslt = new Color();
        List<String> samples = new ArrayList<>();
        Map<String, Integer> targetSteps = new HashMap<>();
        Map<String, Integer> targetAlphaHisto = new HashMap<>();
        java.util.Set<Integer> dumpedFaces = new java.util.HashSet<>();
        boolean dumpTiles = target.blockName.contains("copper_golem_statue");
        Map<String, double[]> patchExtents = new java.util.TreeMap<>();
        java.util.List<Hit>[][] pipeHits = new java.util.ArrayList[tileSize][tileSize];

        for (int px = 0; px < tileSize; px += 1) {
            double pxc = xbase + px + 0.5;
            for (int py = 0; py < tileSize; py += 1) {
                double pyc = ybase + py + 0.5;
                Vector3D top = new Vector3D(
                        zb.x + xstep.x * pxc + ystep.x * pyc,
                        zb.y + xstep.y * pxc + ystep.y * pyc,
                        zb.z + xstep.z * pxc + ystep.z * pyc);
                topF.set(ps, top);
                bottomF.set(ps, new Vector3D(top.x + dirv.x, top.y + dirv.y, top.z + dirv.z));
                pxF.setInt(ps, px);
                pyF.setInt(ps, py);
                rec.hits.clear();
                rec.px = px; rec.py = py; rec.tx = top.x; rec.tz = top.z;
                resetM.invoke(rec, ps);
                boolean[] done = new boolean[] { false };
                try {
                    raytrace.invoke(ps, cache, new HDShaderState[] { rec }, done);
                } catch (Exception ex) {
                    if (samples.size() < 3) samples.add("raytrace error at " + px + "," + py + ": " + ex);
                    continue;
                }
                if (!done[0]) rayFinished.invoke(rec, ps);
                getRayColor.invoke(rec, rslt, Integer.valueOf(0));
                int argb = rslt.getARGB();

                boolean targetHit = false;
                for (Hit h : rec.hits) {
                    if (pipeHits[px][py] == null) pipeHits[px][py] = new java.util.ArrayList<>();
                    pipeHits[px][py].add(h);
                    if (h.blk.equals(target)) {
                        targetHit = true; targetVisited++; targetSteps.merge(String.valueOf(h.step), 1, Integer::sum);
                        if (dumpTiles) {
                            String key = "p" + h.patchid + "/t" + h.txtId + "/" + h.step;
                            double[] ex = patchExtents.get(key);
                            if (ex == null) { ex = new double[] { h.u, h.u, h.v, h.v, 0 }; patchExtents.put(key, ex); }
                            ex[0] = Math.min(ex[0], h.u); ex[1] = Math.max(ex[1], h.u);
                            ex[2] = Math.min(ex[2], h.v); ex[3] = Math.max(ex[3], h.v); ex[4]++;
                        }
                        String bucket = (h.alpha <= 0) ? "a=0"
                                : (h.alpha < 64) ? "a=1-63"
                                : (h.alpha < 128) ? "a=64-127"
                                : (h.alpha < 255) ? "a=128-254" : "a=255";
                        targetAlphaHisto.merge("step=" + h.step + " " + bucket, 1, Integer::sum);
                        if (dumpTiles && dumpedFaces.add(h.step.ordinal() * 100000 + h.txtId)) {
                            int nu = (int) Math.floor(h.u * 16), nv = 16 - (int) Math.floor(h.v * 16) - 1;
                            int ru = (int) Math.floor(h.u * 4), rv = 4 - (int) Math.floor(h.v * 4) - 1;
                            System.out.println("  [" + label + "] FACE patch=" + h.patchid + " textid=" + h.txtId + " step=" + h.step
                                    + "  hitU=%.4f hitV=%.4f".formatted(h.u, h.v)
                                    + " nativePx(%d,%d) resampledPx(%d,%d) sampledAlpha=".formatted(nu, nv, ru, rv) + h.alpha);
                            dumpFaceTile(tp, h.txtId, "[" + label + "] NATIVE textid=" + h.txtId);
                            dumpFaceTile(rec.samplePack, h.txtId, "[" + label + "] USED textid=" + h.txtId);
                            for (int i = 0; i < rec.hits.size(); i++) {
                                Hit hh = rec.hits.get(i);
                                if (hh.txtId == h.txtId) {
                                    System.out.println("      hit#" + i + " patch=" + hh.patchid + " step=" + hh.step + " u=%.4f v=%.4f".formatted(hh.u, hh.v) + " alpha=" + hh.alpha);
                                }
                            }
                        }
                    }
                }
                if (argb == 0) continue;
                rendered++;
                if (targetHit) {
                    Hit last = rec.hits.get(rec.hits.size() - 1);
                    if (last.blk.equals(target)) {
                        targetBecameLastOpaque++;
                        if (samples.size() < 3)
                            samples.add(String.format("[%s] px(%d,%d) TARGET-OPAQUE argb=%08x lastTex=%d step=%s u=%.3f v=%.3f",
                                    label, px, py, argb, last.txtId, last.step, last.u, last.v));
                    } else {
                        targetHitButTransparent++;
                        if (samples.size() < 6)
                            samples.add(String.format("[%s] px(%d,%d) target-HIT-but-alpha=0 argb=%08x lastBlock=%s lastTex=%d",
                                    label, px, py, argb, last.blk, last.txtId));
                    }
                }
            }
        }
        System.out.println("\n===== " + label + " =====");
        System.out.println("window origin " + String.format("(%.0f,%.0f)", xbase, ybase)
                + " size " + tileSize + " map-units");
        System.out.println("rendered terrain pixels      : " + rendered);
        System.out.println("target visited (processBlock): " + targetVisited);
        System.out.println("target does NOT produce final: " + targetHitButTransparent);
        System.out.println("target = last opaque producer : " + targetBecameLastOpaque);
        System.out.println("target hit steps              : " + targetSteps);
        System.out.println("target alpha histogram        : " + targetAlphaHisto);
        if (dumpTiles) {
            System.out.println("PATCH UV EXTENTS (face uv-span in tile-fraction, hits=n):");
            for (Map.Entry<String, double[]> e : patchExtents.entrySet()) {
                double[] ex = e.getValue();
                System.out.printf("  %-18s u[%6.4f..%6.4f] w=%.4f  v[%6.4f..%6.4f] w=%.4f  n=%.0f natTexW=%5.2f nath=%5.2f%n",
                        e.getKey(), ex[0], ex[1], ex[1] - ex[0], ex[2], ex[3], ex[3] - ex[2], ex[4],
                        (ex[1] - ex[0]) * 16, (ex[3] - ex[2]) * 16);
            }
            if (label.contains("FLAT")) {
                System.out.println("PER-PIXEL TARGET HITS (px py | top.x top.z -> block bx,by,bz | u v alpha) first 48:");
                int npp = 0;
                for (int px = 0; px < tileSize && npp < 48; px++) {
                    for (int py = 0; py < tileSize && npp < 48; py++) {
                        if (pipeHits[px][py] == null) continue;
                        for (Hit h : pipeHits[px][py]) {
                            if (h.blk.equals(target)) {
                                System.out.printf("  px=%4d py=%4d | tx=%6.3f tz=%6.3f -> block(%d,%d,%d) | u=%.4f v=%.4f a=%d%n",
                                        h.px, h.py, h.tx, h.tz, h.bx, h.by, h.bz, h.u, h.v, h.alpha);
                                if (++npp >= 48) break;
                            }
                        }
                    }
                }
            }
        }
        for (String s : samples) System.out.println("  " + s);
        rec.ss.cleanup();
        return new TraceStats(rendered, targetVisited, targetHitButTransparent, targetBecameLastOpaque);
    }

    @Test
    void renderFlatVsSurfaceForControlAndSuspicious() throws Exception {
        boot();
        IsoHDPerspective flat = buildPerspective("iso_S_90_lowres", 180.0, 90.0, 4);
        IsoHDPerspective surface = buildPerspective("iso_SE_30_hires", 135.0, 30.0, 16);
        runBlock(flat, surface, "minecraft:chest", "facing=north,type=single,waterlogged=false", "CHEST (control)");
        runBlock(flat, surface, "minecraft:copper_golem_statue",
                "copper_golem_pose=standing,facing=north", "STATUE copper-golem");
        runBlock(flat, surface, "minecraft:shulker_box", "facing=up", "SHULKER-BOX");
        runBlock(flat, surface, "minecraft:glass", null, "GLASS (transparent probe)");
        runBlock(flat, surface, "minecraft:oak_leaves", null, "OAK_LEAVES (transparent probe)");
    }

    private static void runBlock(IsoHDPerspective flat, IsoHDPerspective surface,
            String blockName, String stateName, String title) throws Exception {
        DynmapBlockState target = DynmapBlockState.getBaseStateByName(blockName);
        assertTrue(target != null && target != DynmapBlockState.AIR, blockName + " state must exist");
        System.out.println("\n######## / " + title + " / " + blockName + " / ########");
        System.out.println(blockName + " stateName=" + target.getState(0).stateName
                + " stateCount=" + target.getStateCount());
        HDBlockModel targetModel = HDBlockModels.models_by_id_data[target.getState(0).globalStateIndex];
        System.out.println(blockName + " model: " + targetModel);
        assertTrue(targetModel != null, blockName + " model must be loaded");
        if (targetModel instanceof HDBlockPatchModel) {
            System.out.println(blockName + " patches=" + ((HDBlockPatchModel) targetModel).getPatches().length);
        }

        GenericMapChunkCache cache = buildCache(blockName);
        TraceStats nat = traceTile(flat, cache, "FLAT    iso_S_90 top-down (native tiles, scale 4)", 192, false, target.getState(0));
        TraceStats res = traceTile(flat, cache, "FLAT    iso_S_90 top-down (RESAMPLED tiles like production)", 192, true, target.getState(0));
        TraceStats surf = traceTile(surface, cache, "SURFACE iso_SE_30 (RESAMPLED tiles, scale 16)", 192, true, target.getState(0));
        System.out.println("BASELINE " + blockName
                + " native" + nat.csv() + " resampled" + res.csv() + " surface" + surf.csv());
        assertTrue(stateName == null || DynmapBlockState.getBaseStateByName(blockName).getState(0).stateName.equals(stateName),
                blockName + " registration mismatch");
        assertBlockOcclusion(blockName, nat, res, surf);
    }

    /** Harness proof for the occluding layer metadata: the statue (occluding) must close all its pixels
     *  against the block behind it in EVERY projection, while every non-occluding model must keep its
     *  exact pre-change behaviour (regression pin). Values are deterministic for this synthetic world. */
    private static void assertBlockOcclusion(String blockName, TraceStats nat, TraceStats res, TraceStats surf) {
        assertEquals(36352, nat.rendered(), blockName + " native: all pixels keep rendering");
        assertEquals(36352, res.rendered(), blockName + " FLAT-resampled: all pixels keep rendering");
        assertEquals(36864, surf.rendered(), blockName + " surface: all pixels keep rendering");
        switch (blockName) {
            case "minecraft:copper_golem_statue" -> {
                assertEquals(12, nat.targetVisited(), "STATUE native visited");
                assertEquals(0, nat.targetHitButTransparent(), "STATUE native: no false-pierce to block behind");
                assertEquals(12, nat.targetBecameLastOpaque(), "STATUE native: statue ends every statue pixel");
                assertEquals(24, res.targetVisited(), "STATUE FLAT-resampled visited");
                assertEquals(0, res.targetHitButTransparent(), "STATUE FLAT-resampled: no grass fill behind (occluding)");
                assertEquals(12, res.targetBecameLastOpaque(), "STATUE FLAT-resampled scale-4: statue ends every statue pixel");
                assertEquals(0, surf.targetHitButTransparent(), "STATUE surface: no block behind statue");
                assertEquals(149, surf.targetBecameLastOpaque(), "STATUE surface: statue ends every statue pixel");
            }
            case "minecraft:chest" -> {
                assertEquals(144, nat.targetBecameLastOpaque(), "CHEST native unchanged");
                assertEquals(108, res.targetBecameLastOpaque(), "CHEST FLAT-resampled unchanged");
                assertEquals(36, res.targetHitButTransparent(), "CHEST FLAT-resampled unchanged");
                assertEquals(258, surf.targetBecameLastOpaque(), "CHEST surface unchanged");
            }
            case "minecraft:shulker_box" -> {
                assertEquals(36, res.targetBecameLastOpaque(), "SHULKER FLAT-resampled unchanged");
                assertEquals(0, res.targetHitButTransparent(), "SHULKER FLAT-resampled unchanged");
                assertEquals(154, surf.targetBecameLastOpaque(), "SHULKER surface unchanged");
            }
            case "minecraft:glass" -> {
                assertEquals(0, res.targetBecameLastOpaque(), "GLASS FLAT-resampled unchanged");
                assertEquals(144, res.targetHitButTransparent(), "GLASS FLAT-resampled unchanged");
                assertEquals(99, surf.targetBecameLastOpaque(), "GLASS surface unchanged");
            }
            case "minecraft:oak_leaves" -> {
                assertEquals(0, res.targetBecameLastOpaque(), "OAK_LEAVES FLAT-resampled unchanged");
                assertEquals(144, res.targetHitButTransparent(), "OAK_LEAVES FLAT-resampled unchanged");
                assertEquals(285, surf.targetBecameLastOpaque(), "OAK_LEAVES surface unchanged");
            }
            default -> { }
        }
    }
}
