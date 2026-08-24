package org.dynmap.hdmap;

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
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;
import org.dynmap.exporter.OBJExport;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.VisibilityLimit;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * TEMPORARY DIAGNOSTIC - white stripe on grass block sides.
 * Reproduces the real render path headlessly: vanilla JSON models + standard
 * texture pack + real raytrace over synthetic plains chunks, then reports which
 * patch/texture produces near-white pixels at the top of side faces.
 */
class GrassSideStripeDiagnosticTest {
    private static final int TINT_MULT = 1_000_000;

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

    // ---- minimal NBT model (copied from GenericChunkBiomeParseTest) ----
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

    /** Section Y=4: air everywhere except grass at localY=6 and dirt at localY=5. */
    private static FakeCompound surfaceSection() { return surfaceSection("minecraft:plains"); }

    private static FakeCompound surfaceSection(String biome) {
        FakeList palette = new FakeList();
        palette.add(new FakeCompound().put("Name", "minecraft:air"));
        palette.add(new FakeCompound().put("Name", "minecraft:dirt"));
        palette.add(new FakeCompound().put("Name", "minecraft:grass_block"));
        int[] idx = new int[16 * 16 * 16];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int i = y * 256 + z * 16 + x;
                    idx[i] = (y == 6) ? 2 : ((y == 5) ? 1 : 0);
                }
        FakeCompound bs = new FakeCompound().put("palette", palette).put("data", packStates(4, idx));
        FakeCompound biomes = new FakeCompound().put("palette", new FakeList().add(biome));
        return new FakeCompound().put("Y", (byte) 4).put("block_states", bs).put("biomes", biomes);
    }

    private static FakeCompound chunkNbt(int cx, int cz) { return chunkNbt(cx, cz, "minecraft:plains"); }

    private static FakeCompound chunkNbt(int cx, int cz, String biome) {
        FakeCompound root = new FakeCompound();
        root.put("DataVersion", 4440).put("xPos", cx).put("zPos", cz)
                .put("Status", "minecraft:full")
                .put("sections", new FakeList().add(surfaceSection(biome)));
        return root;
    }

    private static GenericMapChunkCache buildCache() throws Exception {
        List<DynmapChunk> chunks = new ArrayList<>();
        for (int cz = -1; cz <= 1; cz++)
            for (int cx = -1; cx <= 1; cx++)
                chunks.add(new DynmapChunk(cx, cz));
        return buildCache(chunks, "minecraft:plains");
    }

    private static GenericMapChunkCache buildCache(List<DynmapChunk> chunks, String biome) throws Exception {
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
        List<DynmapChunk> allChunks = chunks;
        cache.setChunks(WORLD, allChunks);
        Field snaparrayF = GenericMapChunkCache.class.getDeclaredField("snaparray");
        snaparrayF.setAccessible(true);
        GenericChunk[] snaps = (GenericChunk[]) snaparrayF.get(cache);
        int xMin = Integer.MAX_VALUE, zMin = Integer.MAX_VALUE;
        for (DynmapChunk c : chunks) { xMin = Math.min(xMin, c.x); zMin = Math.min(zMin, c.z); }
        int xDim = (int) chunks.stream().map(c -> c.x).distinct().count();
        for (DynmapChunk c : chunks) {
            GenericChunk parsed = cache.parseChunkFromNBT(chunkNbt(c.x, c.z, biome));
            assertNotNull(parsed, "chunk " + c.x + "," + c.z + " must parse");
            snaps[(c.x - xMin) + (c.z - zMin) * xDim] = parsed;
        }
        return cache;
    }

    // ---- boot ----
    private static DynmapCore core;
    private static TexturePack tp;
    private static IsoHDPerspective persp;

    /** Minimal server stub - only openResource is functional. */
    static final class StubServer extends DynmapServerInterface {
        @Override
        public InputStream openResource(String modid, String rname) {
            return GrassSideStripeDiagnosticTest.class.getClassLoader()
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

        // Register the vanilla states we need BEFORE loading - mirrors the platform
        // adapter populating DynmapBlockState from server registries in production.
        // Idempotent: re-registering on a second boot() in the same JVM would append
        // duplicate state entries and break getState(0) identity checks.
        if (DynmapBlockState.getBaseStateByName("minecraft:grass_block") == DynmapBlockState.AIR) {
            new DynmapBlockState.Builder().setBlockName("minecraft:dirt").setAttenuatesLight(15).build();
            DynmapBlockState grassBase = new DynmapBlockState.Builder().setBlockName("minecraft:grass_block")
                    .setStateName("snowy=false").setAttenuatesLight(15).build();
            new DynmapBlockState.Builder().setBaseState(grassBase).setStateIndex(1)
                    .setBlockName("minecraft:grass_block").setStateName("snowy=true").setAttenuatesLight(15).build();
            new DynmapBlockState.Builder().setBlockName("minecraft:stone").setAttenuatesLight(15).build();
        }

        HDBlockStateTextureMap.initializeTable();
        TexturePack.resetFiles();
        HDBlockModels.models_by_id_data = new HDBlockModel[DynmapBlockState.getGlobalIndexMax() + 1];
        new MinecraftModelLoader(core.getMinecraftResourceProvider(), HDBlockModels.getPatchDefinitionFactory()).load();

        // Minimal MapManager singleton so TexturePackHDShader can construct
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

        ConfigurationNode perspCfg = new ConfigurationNode();
        perspCfg.put("name", "diag_iso");
        perspCfg.put("scale", Integer.valueOf(4));
        perspCfg.put("maximumheight", Integer.valueOf(96));
        perspCfg.put("minimumheight", Integer.valueOf(-64));
        persp = new IsoHDPerspective(core, perspCfg);
    }

    private static TexturePackHDShader shader;

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static String tileNameOf(int textid) {
        int mod = textid / TINT_MULT;
        int raw = textid % TINT_MULT;
        String modname = switch (mod) {
            case 0 -> "";
            case 1 -> "GRASSTONED";
            case 2 -> "FOLIAGETONED";
            case 3 -> "WATERTONED";
            case 4 -> "CLEARINSIDE";
            default -> "MOD" + mod;
        };
        String fname = "?";
        try {
            Field imgsF = TexturePack.class.getDeclaredField("imgs");
            imgsF.setAccessible(true);
            Object[] imgs = (Object[]) imgsF.get(tp);
            if (raw >= 0 && raw < imgs.length && imgs[raw] != null) {
                Field fnF = imgs[raw].getClass().getDeclaredField("fname");
                fnF.setAccessible(true);
                Object fn = fnF.get(imgs[raw]);
                if (fn != null) fname = fn.toString();
            }
        } catch (Exception e) {
            fname = "err:" + e.getMessage();
        }
        return "[" + raw + "]" + fname + (modname.isEmpty() ? "" : ":" + modname);
    }

    private static String argb(int a) {
        int r = (a >> 16) & 0xFF, g = (a >> 8) & 0xFF, b = a & 0xFF;
        return String.format("%02X%02X%02X%02X", (a >> 24) & 0xFF, r, g, b);
    }

    private static boolean isWhitish(int a) {
        int r = (a >> 16) & 0xFF, g = (a >> 8) & 0xFF, b = a & 0xFF;
        int mn = Math.min(r, Math.min(g, b)), mx = Math.max(r, Math.max(g, b));
        return ((a >> 24) & 0xFF) > 200 && mn > 180 && (mx - mn) < 30;
    }

    /** Fake perspective state with fully controllable patch inputs. */
    static final class FakePS implements HDPerspectiveState {
        int patchid = -1;
        double u, v;
        BlockStep laststep = BlockStep.Y_MINUS;
        DynmapBlockState blk = DynmapBlockState.AIR;
        @Override public void getLightLevels(LightLevels ll) { ll.sky = 15; ll.emitted = 0; }
        @Override public void getLightLevelsAtStep(BlockStep step, LightLevels ll) { ll.sky = 15; ll.emitted = 0; }
        @Override public DynmapBlockState getBlockState() { return blk; }
        @Override public BlockStep getLastBlockStep() { return laststep; }
        @Override public BlockStep getShadeStep() { return laststep; }
        @Override public double getScale() { return 4.0; }
        @Override public Vector3D getRayStart() { return new Vector3D(); }
        @Override public Vector3D getRayEnd() { return new Vector3D(); }
        @Override public int getPixelX() { return 0; }
        @Override public int getPixelY() { return 0; }
        @Override public boolean getShade() { return true; }
        @Override public int getSubmodelAlpha() { return -1; }
        @Override public int[] getSubblockCoord() { return new int[] { 0, 0, 0 }; }
        @Override public boolean isOnFace() { return false; }
        @Override public MapIterator getMapIterator() { return null; }
        @Override public int getTextureIndex() { return patchid; }
        @Override public double getPatchU() { return u; }
        @Override public double getPatchV() { return v; }
        @Override public LightLevels getCachedLightLevels(int idx) { LightLevels ll = new LightLevels(); ll.sky = 15; ll.emitted = 0; return ll; }
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

    /** Real ShaderState instance with lighting injected, textures left at native scale. */
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
        assertTrue(tp != null, "standard texture pack must load lazily with shader state");

        // Force native-resolution tiles (exactly the registered vanilla pixels)
        Field stpF = TexturePackHDShader.ShaderState.class.getDeclaredField("scaledtp");
        stpF.setAccessible(true);
        stpF.set(ss, tp);
        return ss;
    }

    private static Method readColorMethod() throws Exception {
        Method m = TexturePack.class.getDeclaredMethod("readColor", HDPerspectiveState.class,
                MapIterator.class, Color.class, DynmapBlockState.class,
                TexturePackHDShader.ShaderState.class, HDBlockStateTextureMap.class,
                BlockStep.class, int.class, int.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    /**
     * Harness A: enumerate every registered patch of grass_block and dump its rendered
     * 16x16 color grid through the REAL readColor math (modifiers, colormap, blending).
     */
    @Test
    void enumerateGrassPatchesAndColors() throws Exception {
        boot();
        GenericMapChunkCache cache = buildCache();
        MapIterator mapiter = cache.getIterator(8, 70, 8);
        TexturePackHDShader.ShaderState ss = makeShaderState(cache, mapiter);
        DynmapBlockState grass = DynmapBlockState.getBaseStateByName("minecraft:grass_block").getState(0);
        HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(grass);
        assertTrue(map != null && map.faces != null, "grass_block must have a state texture map");

        Method readColor = readColorMethod();
        FakePS ps = new FakePS();
        ps.blk = grass;
        Color rslt = new Color();

        System.out.println("=== faces of grass_block[snowy=false]: " + map.faces.length + " entries ===");
        for (int pid = 0; pid < map.faces.length; pid++) {
            int textid = map.faces[pid];
            if (textid < 0) {
                System.out.println("patch " + pid + ": <none>");
                continue;
            }
            StringBuilder sb = new StringBuilder("patch " + pid + ": " + tileNameOf(textid)
                    + " raw=0x" + Integer.toHexString(textid) + "\n");
            for (int row = 0; row < 16; row++) {
                for (int col = 0; col < 16; col++) {
                    ps.patchid = pid;
                    ps.u = (col + 0.5) / 16.0;
                    ps.v = (row + 0.5) / 16.0;
                    ps.laststep = BlockStep.Z_PLUS; // horizontal face entry like an iso view of a side
                    readColor.invoke(tp, ps, mapiter, rslt, grass, ss, map, ps.laststep, pid, textid, map.stdrotate);
                    sb.append(argb(rslt.getARGB())).append(' ');
                }
                sb.append('\n');
            }
            System.out.print(sb);
        }

        // Also dump what the base side texture looks like when entered from each horizontal step
        System.out.println("=== side-face sampling via laststep sweep (patch id = face index fallback) ===");
        for (BlockStep step : new BlockStep[] { BlockStep.X_PLUS, BlockStep.X_MINUS, BlockStep.Z_PLUS, BlockStep.Z_MINUS }) {
            ps.patchid = -1;
            ps.laststep = step;
            ps.u = 0.5; ps.v = 0.03;
            int textidFallback = map.faces.length > step.ordinal() ? map.faces[step.ordinal()] : -1;
            readColor.invoke(tp, ps, mapiter, rslt, grass, ss, map, step,
                    -1, textidFallback, map.stdrotate);
            System.out.println("faceindex=" + step.ordinal() + " (" + step + ") v=0.03 -> "
                    + (rslt.isTransparent() ? "transparent" : argb(rslt.getARGB()))
                    + " tex=" + (map.faces.length > step.ordinal() ? tileNameOf(map.faces[step.ordinal()]) : "<none>"));
        }

        // Raw tile inspection: identify tiles behind grass_block faces and their alpha coverage
        System.out.println("=== raw tiles behind grass_block faces ===");
        dumpTiles(tp, map);

        // Same inspection on the RESAMPLED pack used in production renders (scale > 1)
        TexturePack scaled = tp.resampleTexturePack(4);
        System.out.println("=== resampled x4 tiles ===");
        dumpTiles(scaled, map);

        // readColor through the SCALED pack - what production rays actually sample
        System.out.println("=== readColor via resampled pack, patch grids ===");
        Field stpF = TexturePackHDShader.ShaderState.class.getDeclaredField("scaledtp");
        stpF.setAccessible(true);
        stpF.set(ss, scaled);
        for (int pid : new int[] { 2, 6 }) {
            int textid = map.faces[pid];
            StringBuilder sb = new StringBuilder("scaled patch " + pid + ": " + tileNameOf(textid) + "\n");
            for (int row = 0; row < 16; row++) {
                for (int col = 0; col < 16; col++) {
                    ps.patchid = pid;
                    ps.u = (col + 0.5) / 16.0;
                    ps.v = (row + 0.5) / 16.0;
                    ps.laststep = BlockStep.Z_PLUS;
                    readColor.invoke(tp, ps, mapiter, rslt, grass, ss, map, ps.laststep, pid, textid, map.stdrotate);
                    sb.append(argb(rslt.getARGB())).append(' ');
                }
                sb.append('\n');
            }
            System.out.print(sb);
        }
        stpF.set(ss, tp);
    }

    private static void dumpTiles(TexturePack pack, HDBlockStateTextureMap map) throws Exception {
        Field tilesF = TexturePack.class.getDeclaredField("tile_argb");
        tilesF.setAccessible(true);
        int[][] tileArgb = (int[][]) tilesF.get(pack);
        java.util.LinkedHashSet<Integer> want = new java.util.LinkedHashSet<>();
        for (int f : map.faces) want.add(f % TINT_MULT);
        for (int t : want) {
            if (t < 0 || t >= tileArgb.length || tileArgb[t] == null) { System.out.println("tile " + t + ": <null>"); continue; }
            int[] pxl = tileArgb[t];
            int opaque = 0, minA = 255, maxA = 0;
            long rSum = 0, gSum = 0, bSum = 0; int cnt = 0;
            for (int p : pxl) {
                int a = (p >>> 24) & 0xFF;
                minA = Math.min(minA, a); maxA = Math.max(maxA, a);
                if (a > 128) {
                    opaque++;
                    rSum += (p >> 16) & 0xFF; gSum += (p >> 8) & 0xFF; bSum += p & 0xFF; cnt++;
                }
            }
            StringBuilder sb = new StringBuilder("tile " + t + ": opaque " + opaque + "/" + pxl.length
                    + " alphaRange[" + minA + "," + maxA + "]");
            if (cnt > 0) sb.append(String.format(" avgVisible=(%d,%d,%d)", rSum / cnt, gSum / cnt, bSum / cnt));
            sb.append(" row0=");
            for (int x = 0; x < 16 && x < pxl.length; x++) sb.append(argb(pxl[x])).append(' ');
            System.out.println(sb);
        }
    }

    /**
     * Harness B: real raytrace over the synthetic world; report per-pixel producer
     * patches and flag near-white pixels coming off grass block sides.
     */
    @Test
    void raytraceSurfaceAndReportWhitePixels() throws Exception {
        boot();
        int[] r = traceTile(buildCache(), "plains");
        System.out.println("=== raytrace summary (valid biome data) ===");
        System.out.println("rendered pixels: " + r[0] + ", whitish-over-grass-side pixels: " + r[1]
                + ", whitish-grass-top pixels: " + r[2]);
        assertTrue(r[0] > 0, "rays must hit terrain in synthetic world");
    }

    /**
     * Harness C: same render, but chunk biome palette references an unknown biome -
     * models what happens when server biome data cannot be mapped.
     */
    @Test
    void raytraceWithUnmappableBiome() throws Exception {
        boot();
        List<DynmapChunk> chunks = new ArrayList<>();
        for (int cz = -1; cz <= 1; cz++)
            for (int cx = -1; cx <= 1; cx++)
                chunks.add(new DynmapChunk(cx, cz));
        GenericMapChunkCache cache = buildCache(chunks, "minecraft:totally_unknown_biome");
        MapIterator probeIter = cache.getIterator(8, 70, 8);
        System.out.println("unmappable-biome case: biome at (8,70,8) = " + probeIter.getBiome()
                + " name=" + probeIter.getBiome().toString());
        int[] r = traceTile(cache, "unknown-biome");
        System.out.println("=== raytrace summary (unmappable biome) ===");
        System.out.println("rendered pixels: " + r[0] + ", whitish-over-grass-side pixels: " + r[1]
                + ", whitish-grass-top pixels: " + r[2]);
        assertTrue(r[0] > 0, "rays must hit terrain in synthetic world");
    }

    /** Full-tile raytrace through the real perspective; returns {rendered, whitishOverGrassSide}. */
    private static int[] traceTile(GenericMapChunkCache cache, String label) throws Exception {
        MapIterator mapiter = cache.getIterator(8, 70, 8);
        TexturePackHDShader.ShaderState ss = makeShaderState(cache, mapiter);
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

        double height = 96.0, miny = -64.0;
        int tileSize = 128;
        int tx = 0, ty = 0;
        double xbase = tx * tileSize, ybase = ty * tileSize;

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

        int whiteSidePixels = 0;
        int whiteTopPixels = 0;
        long topR = 0, topG = 0, topB = 0;
        int topCount = 0;
        int renderedPixels = 0;
        Color rslt = new Color();
        List<String> report = new ArrayList<>();

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
                resetM.invoke(rec, ps);
                boolean[] done = new boolean[] { false };
                try {
                    raytrace.invoke(ps, cache, new HDShaderState[] { rec }, done);
                } catch (Exception ex) {
                    if (report.size() < 5) report.add("raytrace error at " + px + "," + py + ": " + ex);
                    continue;
                }
                if (!done[0]) rayFinished.invoke(rec, ps);
                getRayColor.invoke(rec, rslt, Integer.valueOf(0));
                int argb = rslt.getARGB();
                if (argb == 0) continue;
                renderedPixels++;
                boolean touchedGrass = rec.hits.stream().anyMatch(h ->
                        h.blk.equals(DynmapBlockState.getBaseStateByName("minecraft:grass_block").getState(0)));
                if (touchedGrass && rec.hits.stream().allMatch(h ->
                        !h.blk.equals(DynmapBlockState.getBaseStateByName("minecraft:grass_block").getState(0))
                                || h.step == BlockStep.Y_MINUS)) {
                    topR += (argb >> 16) & 0xFF; topG += (argb >> 8) & 0xFF; topB += argb & 0xFF;
                    topCount++;
                }
                if (isWhitish(argb)) {
                    Hit last = rec.hits.get(rec.hits.size() - 1);
                    boolean touchedGrassSide = touchedGrass && last.step != BlockStep.Y_PLUS;
                    if (touchedGrassSide) {
                        whiteSidePixels++;
                        if (report.size() < 40) {
                            report.add(String.format("[%s] WHITE px(%d,%d) argb=%s lastHit tex=%d (%s) u=%.2f v=%.2f step=%s",
                                    label, px, py, argb(argb), last.texid, tileNameOf(last.texid), last.u, last.v, last.step));
                        }
                    }
                    else if (touchedGrass && rec.hits.stream().allMatch(h ->
                            !h.blk.equals(DynmapBlockState.getBaseStateByName("minecraft:grass_block").getState(0))
                                    || h.step == BlockStep.Y_MINUS)) {
                        whiteTopPixels++;
                        if (whiteTopPixels <= 5) {
                            report.add(String.format("[%s] WHITE-TOP px(%d,%d) argb=%s", label, px, py, argb(argb)));
                        }
                    }
                }
            }
        }
        for (String s : report) System.out.println(s);
        if (topCount > 0)
            System.out.println(String.format(
                "[%s] grass-top mean color: R=%d G=%d B=%d (n=%d)",
                label, topR / topCount, topG / topCount, topB / topCount, topCount));
        assertTrue(renderedPixels > 0, "rays must hit terrain in synthetic world");
        rec.ss.cleanup();
        return new int[] { renderedPixels, whiteSidePixels, whiteTopPixels };
    }

    static final class Hit {
        final DynmapBlockState blk; final int texid; final double u, v; final BlockStep step;
        Hit(DynmapBlockState blk, int texid, double u, double v, BlockStep step) {
            this.blk = blk; this.texid = texid; this.u = u; this.v = v; this.step = step;
        }
    }

    /** Delegating shader state that records every processBlock texture selection. */
    static final class RecordingState implements HDShaderState {
        final TexturePackHDShader.ShaderState ss;
        final List<Hit> hits = new ArrayList<>();
        RecordingState(TexturePackHDShader.ShaderState ss) { this.ss = ss; }
        @Override public boolean processBlock(HDPerspectiveState ps) {
            if (ps.getTextureIndex() >= 0 && !ps.getBlockState().isAir()) {
                hits.add(new Hit(ps.getBlockState(), ps.getTextureIndex(), ps.getPatchU(), ps.getPatchV(), ps.getLastBlockStep()));
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
}
