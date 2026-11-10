package org.dynmap;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.dynmap.common.*;
import org.dynmap.common.chunk.*;
import org.dynmap.hdmap.*;
import org.dynmap.storage.*;
import org.dynmap.storage.filetree.FileTreeMapStorage;
import org.dynmap.utils.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;

class RenderLifecycleTest {
    @TempDir Path directory;

    private static DynmapCommandSender sender(List<String> messages, boolean player, boolean permitted) {
        return (DynmapCommandSender) Proxy.newProxyInstance(RenderLifecycleTest.class.getClassLoader(),
                new Class<?>[]{player ? DynmapPlayer.class : DynmapCommandSender.class}, (p, m, a) -> {
                    if (m.getName().equals("sendMessage")) { messages.add((String)a[0]); return null; }
                    if (m.getName().equals("hasPrivilege")) return permitted;
                    if (m.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    private static void performance(Fixture f, DynmapCommandSender sender, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "performance";
        System.arraycopy(args, 0, command, 1, args.length);
        assertTrue(f.core.processCommand(sender, "dynmap", "airmap", command));
    }

    @ParameterizedTest @ValueSource(strings = {"adaptive", "observe", "legacy", "off"})
    void runtimeCommandsRestoreNondefaultConfigAcrossControllerModes(String mode) throws Exception {
        Fixture f = new Fixture(!mode.equals("off"), mode, 1);
        var pool = f.pool();
        try {
            var console = sender(new ArrayList<>(), false, false);
            performance(f, console, "tps", "16", "10");
            performance(f, console, "parallelrendercnt", Integer.toString(Math.min(2, Runtime.getRuntime().availableProcessors())));
            f.manager.updateTPS(9);
            assertTrue(f.manager.getTPSFullRenderPause());
            assertFalse(f.manager.getTPSUpdateRenderPause());
            performance(f, console, "reset");
            var c = (AdaptiveRenderController)field(f.manager, "adaptiveController");
            assertEquals(1, c.maximumWorkers());
            assertNull(field(f.manager, "workerOverride"));
            assertFalse((boolean)field(f.manager, "tpsOverride"));
            if (mode.equals("off")) assertTrue(f.manager.getTPSUpdateRenderPause(), "manual config TPS pauses restored");
            performance(f, console, "tps", "15", "8");
            Fixture restarted = new Fixture(!mode.equals("off"), mode, 1);
            assertFalse((boolean)field(restarted.manager, "tpsOverride"));
            assertNull(field(restarted.manager, "workerOverride"));
            assertEquals(18, ((AdaptiveRenderController)field(restarted.manager, "adaptiveController")).slowTps());
        } finally { f.manager.beginShutdown(); pool.shutdownNow(); }
    }

    @Test void performanceCommandsValidatePermissionsResetAndShutdown() throws Exception {
        Fixture f = new Fixture(true);
        var pool = f.pool();
        var messages = new ArrayList<String>();
        var console = sender(messages, false, false);
        try {
            performance(f, sender(messages, true, false), "tps", "16", "10");
            assertFalse((boolean)field(f.manager, "tpsOverride"));
            performance(f, sender(messages, true, true), "tps", "16", "10");
            var controller = (AdaptiveRenderController)field(f.manager, "adaptiveController");
            assertEquals(16, controller.slowTps());
            for (String[] invalid : List.of(new String[]{"tps", "NaN", "10"}, new String[]{"tps", "16", "16"},
                    new String[]{"tps", "20", "10"}, new String[]{"tps", "16", "0"},
                    new String[]{"parallelrendercnt", "0"}, new String[]{"parallelrendercnt", "-1"},
                    new String[]{"parallelrendercnt", "2147483647"}, new String[]{"parallelrendercnt", "abc"})) {
                performance(f, console, invalid);
                assertEquals(16, controller.slowTps());
                assertNull(field(f.manager, "workerOverride"));
            }
            performance(f, console, "parallelrendercnt", "1");
            performance(f, console, "status");
            assertTrue(messages.getLast().contains("worker ceiling=1"));
            assertTrue(messages.getLast().contains("tps=true parallelrendercnt=1"));
            assertEquals(List.of("status"), f.core.getTabCompletions(console, "dynmap", new String[]{"performance", "st"}));
            performance(f, console, "reset");
            assertNull(field(f.manager, "workerOverride"));
            assertFalse((boolean)field(f.manager, "tpsOverride"));
            assertEquals(18, controller.slowTps());
            assertEquals(12, controller.criticalTps());
            assertSame(controller, field(f.manager, "adaptiveController"));
            assertEquals(0, f.core.configuration.getObject("parallelrendercnt"));
            f.manager.beginShutdown();
            performance(f, console, "tps", "15", "9");
            assertTrue(messages.getLast().contains("not running"));
            assertEquals(18, controller.slowTps());
        } finally { f.manager.beginShutdown(); pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void criticalTpsKeepsFullProgressAndOnlyAdmitsOneBoundedUpdate(boolean adaptive) throws Exception {
        Fixture f = new Fixture(adaptive);
        var pool = f.pool();
        try {
            performance(f, sender(new ArrayList<>(), false, false), "tps", "18", "12");
            f.manager.updateTPS(11);
            Runnable full = f.full(new Tile(f.world, "0"));
            var rendered = new java.util.concurrent.atomic.AtomicInteger();
            f.world.render = rendered::incrementAndGet;
            full.run();
            assertEquals(0, rendered.get());
            assertEquals(1, ((List<?>)field(full, "renderQueue")).size());
            assertTrue(f.manager.getTPSFullRenderPause());
            assertFalse(f.manager.getTPSUpdateRenderPause());
            assertEquals(5, f.manager.getMaxChunkLoadsPerTick());
            f.update(new Tile(f.world, "1")).run();
            assertEquals(1, rendered.get());
            f.update(new Tile(f.world, "2")).run();
            assertEquals(1, rendered.get(), "even already dequeued updates must obey the rate budget");
            f.manager.updateTPS(12.4);
            assertTrue(f.manager.getTPSFullRenderPause());
            f.manager.updateTPS(12.5);
            assertFalse(f.manager.getTPSFullRenderPause());
            assertEquals(5, f.manager.getMaxChunkLoadsPerTick());
            f.manager.updateTPS(18.5);
            assertTrue(f.manager.getMaxChunkLoadsPerTick() > 5);
        } finally { f.manager.beginShutdown(); pool.shutdownNow(); }
    }

    @Test void runtimeCeilingCanChangeDuringFullRenderWithoutLosingOrDuplicatingTiles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 2);
        Fixture f = new Fixture();
        var pool = f.pool();
        var console = sender(new ArrayList<>(), false, false);
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1), completed = new CountDownLatch(5);
        var counts = new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();
        try {
            performance(f, console, "parallelrendercnt", "2");
            Runnable full = f.full(new Tile(f.world, "0"));
            @SuppressWarnings("unchecked") List<MapTile> queue = (List<MapTile>)field(full, "renderQueue");
            for (int i = 1; i < 4; i++) queue.add(new Tile(f.world, Integer.toString(i)));
            f.world.renderTile = id -> {
                if (id < 2) { entered.countDown(); await(release); }
                counts.merge(id, 1, Integer::sum);
                completed.countDown();
            };
            pool.execute(full);
            await(entered);
            int higher = Math.min(4, Runtime.getRuntime().availableProcessors());
            performance(f, console, "parallelrendercnt", Integer.toString(higher));
            assertEquals(3 + higher, pool.getCorePoolSize());
            performance(f, console, "parallelrendercnt", "1");
            var admission = (RenderAdmission)field(f.manager, "renderAdmission");
            assertEquals(2, admission.active(), "running tiles retain their reservations");
            assertEquals(0, admission.acquire(new Object(), false, 1, 1));
            f.update(new Tile(f.world, "4")).run();
            assertFalse(counts.containsKey(4), "updates must share the reduced ceiling with running full tiles");
            assertTrue(pool.getCorePoolSize() >= 5, "joining children retain executor capacity until drain");
            release.countDown();
            await(completed);
            assertEquals(Map.of(0, 1, 1, 1, 2, 1, 3, 1, 4, 1), counts);
        } finally { release.countDown(); f.manager.beginShutdown(); pool.shutdownNow(); }
    }

    @Test void concurrentAdminChangesControllerSamplesAndShutdownKeepConsistentState() throws Exception {
        Fixture f = new Fixture(true);
        var pool = f.pool();
        Constructor<?> constructor = Class.forName("org.dynmap.MapManager$SampleRenderPerformance")
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Runnable sampler = (Runnable)constructor.newInstance(f.manager);
        try (var workers = Executors.newFixedThreadPool(3)) {
            var admin = workers.submit(() -> {
                var console = sender(new ArrayList<>(), false, false);
                for (int i = 0; i < 100; i++) {
                    performance(f, console, "tps", "16", "10");
                    performance(f, console, "parallelrendercnt", "1");
                    performance(f, console, "reset");
                }
            });
            var samples = workers.submit(() -> {
                for (int i = 0; i < 100; i++) { f.manager.updateTPS(i % 2 == 0 ? 20 : 11); sampler.run(); }
            });
            admin.get(10, TimeUnit.SECONDS);
            samples.get(10, TimeUnit.SECONDS);
            var shutdown = workers.submit(f.manager::beginShutdown);
            var change = workers.submit(() -> performance(f, sender(new ArrayList<>(), false, false), "parallelrendercnt", "1"));
            shutdown.get(5, TimeUnit.SECONDS); change.get(5, TimeUnit.SECONDS);
            Integer before = (Integer)field(f.manager, "workerOverride");
            performance(f, sender(new ArrayList<>(), false, false), "reset");
            assertEquals(before, field(f.manager, "workerOverride"));
            var c = (AdaptiveRenderController)field(f.manager, "adaptiveController");
            assertTrue(((AdaptiveRenderController.Settings)field(f.manager, "adaptiveSettings")).workers() <= c.maximumWorkers());
        } finally { f.manager.beginShutdown(); pool.shutdownNow(); }
    }

    static Object field(Object target, String name) throws Exception {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Field f = findField(type, name);
        f.setAccessible(true);
        return f.get(target instanceof Class<?> ? null : target);
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        while (type != null) {
            try { return type.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "test latch timed out"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new CancellationException(); }
    }

    static final class World extends DynmapWorld {
        volatile boolean loaded = true;
        final CountDownLatch unloaded = new CountDownLatch(1);
        Runnable render = () -> { };
        java.util.function.IntConsumer renderTile = id -> render.run();
        World() { super("test:lifecycle", 16, 8, 0); setExtraZoomOutLevels(2); }
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return new DynmapLocation(getName(), 0, 8, 0); }
        @Override public long getTime() { return 0; }
        @Override public boolean hasStorm() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isLoaded() { return loaded; }
        @Override public void setWorldUnloaded() { loaded = false; unloaded.countDown(); }
        @Override public int getLightLevel(int x, int y, int z) { return 15; }
        @Override public int getHighestBlockYAt(int x, int z) { return 8; }
        @Override public boolean canGetSkyLightLevel() { return true; }
        @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
        @Override public String getEnvironment() { return "normal"; }
        @Override public MapChunkCache getChunkCache(List<DynmapChunk> chunks) { return null; }
    }

    public static final class Tile extends MapTile {
        final int id;
        public Tile(DynmapWorld world, String id) { super(world); this.id = Integer.parseInt(id); }
        @Override public boolean render(MapChunkCache cache, String name) { ((World) world).renderTile.accept(id); return false; }
        @Override public List<DynmapChunk> getRequiredChunks() { return List.of(new DynmapChunk(0, 0)); }
        @Override public MapTile[] getAdjecentTiles() { return new MapTile[0]; }
        @Override public int getTileSize() { return 128; }
        @Override public boolean isBiomeDataNeeded() { return false; }
        @Override public boolean isHightestBlockYDataNeeded() { return false; }
        @Override public boolean isRawBiomeDataNeeded() { return false; }
        @Override public boolean isBlockTypeDataNeeded() { return true; }
        @Override public int tileOrdinalX() { return id; }
        @Override public int tileOrdinalY() { return 0; }
        @Override protected String saveTileData() { return Integer.toString(id); }
        @Override public int hashCode() { return Objects.hash(world.getName(), id); }
        @Override public boolean equals(Object o) { return o instanceof Tile t && t.id == id && t.world == world; }
    }

    static final class TestMap extends MapType {
        @Override public void addMapTiles(List<MapTile> list, DynmapWorld w, int x, int y) { list.add(new Tile(w, "0")); }
        @Override public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z) { return List.of(); }
        @Override public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z, int a, int b, int c) { return List.of(); }
        @Override public MapTile[] getAdjecentTiles(MapTile tile) { return new MapTile[0]; }
        @Override public List<DynmapChunk> getRequiredChunks(MapTile tile) { return tile.getRequiredChunks(); }
        @Override public int getTileSize() { return 128; }
        @Override public String getName() { return "surface"; }
        @Override public List<MapType> getMapsSharingRender(DynmapWorld w) { return List.of(this); }
        @Override public List<String> getMapNamesSharingRender(DynmapWorld w) { return List.of(getName()); }
        @Override public String getPrefix() { return "t"; }
    }

    static final class Cache extends GenericMapChunkCache {
        boolean failRay;
        Cache() { super(new GenericChunkCache(32, false)); }
        @Override public boolean isEmpty() { return false; }
        @Override public boolean isEmptySection(int x, int y, int z) { return false; }
        @Override public void unloadChunks() { }
        @Override public MapIterator getIterator(int x, int y, int z) {
            MapIterator iterator = super.getIterator(x, y, z);
            return (MapIterator) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{MapIterator.class}, (p, m, a) -> {
                if (failRay && m.getName().equals("initialize")) {
                    failRay = false;
                    throw new IllegalStateException("injected raytracing snapshot read failure");
                }
                return m.invoke(iterator, a);
            });
        }
    }

    static final class Server extends DynmapServerInterface {
        Supplier<MapChunkCache> data = Cache::new;
        @Override public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> c, boolean b, boolean h, boolean i, boolean r) { return data.get(); }
        @Override public void scheduleServerTask(Runnable r, long delay) { }
        @Override public <T> Future<T> callSyncMethod(Callable<T> c) { return null; }
        @Override public DynmapPlayer[] getOnlinePlayers() { return new DynmapPlayer[0]; }
        @Override public void reload() { }
        @Override public DynmapPlayer getPlayer(String n) { return null; }
        @Override public DynmapPlayer getOfflinePlayer(String n) { return null; }
        @Override public Set<String> getIPBans() { return Set.of(); }
        @Override public String getServerName() { return "test"; }
        @Override public boolean isPlayerBanned(String p) { return false; }
        @Override public String stripChatColor(String s) { return s; }
        @Override public boolean requestEventNotification(DynmapListenerManager.EventType t) { return false; }
        @Override public void broadcastMessage(String s) { }
        @Override public double getCacheHitRate() { return 0; }
        @Override public void resetCacheStats() { }
        @Override public DynmapWorld getWorldByName(String n) { return null; }
        @Override public boolean checkPlayerPermission(String p, String r) { return false; }
        @Override public int getMaxPlayers() { return 0; }
        @Override public int getCurrentPlayers() { return 0; }
        @Override public int isSignAt(String w, int x, int y, int z) { return 0; }
        @Override public String getServerIP() { return ""; }
    }

    /** Encoded images and hashes committed together, as in MariaDB's tile row. */
    static class Storage extends FileTreeMapStorage {
        record Image(byte[] bytes, long hash) { }
        final Map<String, Image> images = new HashMap<>();
        boolean failWrite;
        MapType.ImageVariant failVariant;
        int writes;
        Runnable onWrite = () -> { };
        @Override public MapStorageTile getTile(DynmapWorld w, MapType m, int x, int y, int zoom, MapType.ImageVariant v) {
            String key = m.getPrefix() + "/" + v + "/" + zoom + "/" + x + "/" + y;
            return new MapStorageTile(w, m, x, y, zoom, v) {
                @Override public String getURI() { return key; }
                @Override public void cleanup() { }
                @Override public void enqueueZoomOutUpdate() { w.enqueueZoomOutUpdate(this); }
                @Override public boolean equals(Object other) { return other instanceof MapStorageTile t && key.equals(t.getURI()); }
                @Override public int hashCode() { return key.hashCode(); }
                @Override public boolean exists() { return images.containsKey(key); }
                @Override public boolean matchesHashCode(long hash) { return exists() && images.get(key).hash == hash; }
                @Override public TileRead read() {
                    Image image = images.get(key);
                    if (image == null) return null;
                    TileRead read = new TileRead();
                    read.image = new BufferInputStream(image.bytes);
                    read.hashCode = image.hash;
                    read.format = MapType.ImageEncoding.PNG;
                    read.lastModified = 1;
                    return read;
                }
                @Override public boolean write(long hash, BufferOutputStream bytes, long timestamp) {
                    onWrite.run();
                    if (failWrite || v == failVariant) return false;
                    writes++;
                    if (bytes == null) images.remove(key);
                    else images.put(key, new Image(Arrays.copyOf(bytes.buf, bytes.len), hash));
                    if (zoom == 0) w.enqueueZoomOutUpdate(this);
                    return true;
                }
                @Override public boolean getWriteLock() { return true; }
                @Override public boolean getReadLock(long timeout) { return true; }
                @Override public void releaseWriteLock() { }
                @Override public void releaseReadLock() { }
                @Override public MapStorageTile getZoomOutTile() {
                    MapStorageTile parent = Storage.super.getTile(w, m, x, y, zoom, v).getZoomOutTile();
                    return Storage.this.getTile(w, m, parent.x, parent.y, parent.zoom, v);
                }
            };
        }
    }

    final class Fixture {
        final DynmapCore core = new DynmapCore();
        final World world = new World();
        final TestMap map = new TestMap();
        final Server server = new Server();
        final Storage storage = new Storage();
        final MapManager manager;
        Fixture() throws Exception { this(false); }
        Fixture(boolean adaptive) throws Exception { this(adaptive, "adaptive", 0); }
        Fixture(boolean adaptive, String mode, int configuredWorkers) throws Exception {
            core.setDataFolder(directory.toFile()); core.setServer(server);
            ConfigurationNode config = new ConfigurationNode();
            config.put("render-performance-auto", adaptive ? "normal" : "false"); config.put("save-pending-period", 0);
            config.put("render-performance-controller", mode.equals("off") ? "adaptive" : mode);
            config.put("parallelrendercnt", configuredWorkers);
            core.configuration = config;
            manager = new MapManager(core, config);
            core.mapManager = manager;
            manager.tileQueue.stop(); // Drive admission explicitly; no live server/executor in this fixture.
            manager.worlds.add(world); manager.worldsLookup.put(world.getName(), world);
            world.maps.add(map); world.mapstate.add(new MapTypeState(world, map));
            Field f = DynmapWorld.class.getDeclaredField("storage"); f.setAccessible(true); f.set(world, storage);
        }
        Runnable update(Tile tile) throws Exception {
            TileRenderCoordinator coordinator = (TileRenderCoordinator) field(manager, "tileRenderCoordinator");
            coordinator.requestUpdate(tile);
            set(manager.tileQueue, "pendingcnt", 1);
            Class<?> type = Class.forName("org.dynmap.MapManager$FullWorldRenderState");
            Constructor<?> c = type.getDeclaredConstructor(MapManager.class, MapTile.class); c.setAccessible(true);
            return (Runnable) c.newInstance(manager, tile);
        }
        Runnable full(Tile tile) throws Exception {
            Class<?> type = Class.forName("org.dynmap.MapManager$FullWorldRenderState");
            Constructor<?> c = type.getDeclaredConstructor(MapManager.class, DynmapWorld.class, DynmapLocation.class,
                    DynmapCommandSender.class, String.class, boolean.class, boolean.class); c.setAccessible(true);
            Runnable job = (Runnable) c.newInstance(manager, world, world.getSpawnLocation(), null, null, false, false);
            set(job, "map", map); set(job, "map_index", 0);
            @SuppressWarnings("unchecked") List<MapTile> queue = (List<MapTile>) field(job, "renderQueue");
            queue.add(tile);
            ((TileFlags) field(job, "found")).setFlag(tile.id, 0, true);
            @SuppressWarnings("unchecked") Map<String, Object> active = (Map<String, Object>) field(manager, "active_renders");
            active.put(world.getName(), job);
            return job;
        }
        ScheduledThreadPoolExecutor pool() throws Exception {
            Class<?> type = Class.forName("org.dynmap.MapManager$DynmapScheduledThreadPoolExecutor");
            Constructor<?> constructor = type.getDeclaredConstructor(MapManager.class); constructor.setAccessible(true);
            ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) constructor.newInstance(manager);
            set(manager, "render_pool", pool);
            return pool;
        }
        ConfigurationNode saved() {
            ConfigurationNode saved = new ConfigurationNode(directory.resolve(world.getStorageId() + ".pending").toFile());
            assertTrue(saved.load()); return saved;
        }
        void save() throws Exception { call(manager, "savePending", new Class<?>[]{DynmapWorld.class, boolean.class}, world, false); }
    }

    @Test void cancelledUpdateIsPersistedRestoredAndRetried() throws Exception {
        Fixture f = new Fixture();
        Tile tile = new Tile(f.world, "1");
        f.server.data = () -> { throw new CancellationException("snapshot provider stopped"); };
        f.update(tile).run();
        assertEquals(0, field(f.manager.tileQueue, "pendingcnt"));
        // Simulate an admitted item: it is no longer in the physical queue at save time.
        f.manager.tileQueue.popAll();
        f.save();
        assertEquals(1, f.saved().getNodes("tiles").size());
        Fixture restarted = new Fixture();
        assertNotNull(MapTile.restoreTile(restarted.world, f.saved().getNodes("tiles").getFirst()),
                f.saved().getNodes("tiles").getFirst().toString());
        restarted.manager.loadWorld(restarted.world);
        List<MapTile> restored = restarted.manager.tileQueue.popAll();
        assertEquals(1, restored.size());
        int[] renders = {0}; restarted.world.render = () -> renders[0]++;
        restarted.update((Tile) restored.getFirst()).run();
        assertEquals(1, renders[0]);
        restarted.save();
        assertEquals(0, restarted.manager.tileQueue.size());
    }

    @Test void fullCheckpointIncludesTileRemovedFromItsQueue() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        f.server.data = () -> { entered.countDown(); await(release); return new Cache(); };
        Runnable job = f.full(new Tile(f.world, "2"));
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> result = worker.submit(job);
            await(entered);
            try {
                @SuppressWarnings("unchecked") Map<String, Object> checkpoint = (Map<String, Object>) call(job, "saveState", new Class<?>[0]);
                assertEquals(1, ((List<?>) checkpoint.get("queue")).size());
            } finally { release.countDown(); }
            result.get(5, TimeUnit.SECONDS);
        }
    }

    @Test void periodicCheckpointDoesNotRedispatchAnAdmittedUpdate() throws Exception {
        Fixture f = new Fixture();
        f.update(new Tile(f.world, "1"));
        call(f.manager, "savePending", new Class<?>[]{DynmapWorld.class, boolean.class}, f.world, true);
        assertEquals(1, f.saved().getNodes("tiles").size());
        assertEquals(0, f.manager.tileQueue.size(), "checkpoint must not dispatch a second copy of admitted work");
    }

    @Test void parallelFullRenderKeepsFailedTileAndCommitsSuccessfulSibling() throws Exception {
        Fixture f = new Fixture(); set(f.manager, "parallelrendercnt", 2);
        Runnable job = f.full(new Tile(f.world, "0"));
        @SuppressWarnings("unchecked") List<MapTile> queue = (List<MapTile>) field(job, "renderQueue");
        queue.add(new Tile(f.world, "1"));
        f.world.renderTile = id -> { if (id == 0) throw new IllegalStateException("injected render failure"); };
        ScheduledThreadPoolExecutor pool = f.pool();
        try {
            job.run();
            f.manager.beginShutdown();
            assertFalse((boolean) field(job, "cancelled"));
            assertEquals(List.of(new Tile(f.world, "0")), queue);
            assertTrue(((TileFlags) field(job, "rendered")).getFlag(1, 0));
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shutdownRestartsFullRenderAndRepairsBlackZoomWithUnchangedBase(boolean adaptive) throws Exception {
        Fixture f = new Fixture(adaptive);
        MapStorageTile base = f.storage.getTile(f.world, f.map, 0, 0, 0, MapType.ImageVariant.STANDARD);
        BufferedImage green = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xff228844); green.setRGB(0, 0, 128, 128, pixels, 0, 128);
        base.write(7, green, 1); f.world.freshenZoomOutFiles(); f.world.freshenZoomOutFiles();
        base.getZoomOutTile().write(0, new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB), 2);
        CountDownLatch reading = new CountDownLatch(1), cancel = new CountDownLatch(1);
        f.server.data = () -> { reading.countDown(); await(cancel); throw new CancellationException("snapshot provider stopped"); };
        Runnable job = f.full(new Tile(f.world, "0"));
        ScheduledThreadPoolExecutor pool = f.pool();
        try (ExecutorService shutdown = Executors.newSingleThreadExecutor()) {
            pool.execute(job); await(reading);
            Future<?> stopped = shutdown.submit(f.manager::stopRendering);
            try { await(f.world.unloaded); } finally { cancel.countDown(); }
            stopped.get(5, TimeUnit.SECONDS);
        } finally { cancel.countDown(); pool.shutdownNow(); }
        assertEquals(1, f.saved().getNode("job").getNodes("queue").size());
        Fixture restarted = new Fixture(adaptive); restarted.storage.images.putAll(f.storage.images);
        restarted.manager.loadWorld(restarted.world);
        @SuppressWarnings("unchecked") Map<String, Runnable> jobs = (Map<String, Runnable>) field(restarted.manager, "active_renders");
        Runnable restored = jobs.get(restarted.world.getName()); assertNotNull(restored);
        restarted.world.render = () -> assertTrue(restarted.storage.getTile(restarted.world, restarted.map,
                0, 0, 0, MapType.ImageVariant.STANDARD).matchesHashCode(7));
        restored.run(); restored.run(); // tile, then full-render completion
        assertFalse(restarted.manager.isRenderJobActive(restarted.world.getName()));
        restarted.world.freshenZoomOutFiles();
        MapStorageTile repaired = restarted.storage.getTile(restarted.world, restarted.map, 0, 0, 1, MapType.ImageVariant.STANDARD);
        assertFalse(repaired.matchesHashCode(0));
        BufferedImage result = ImageIO.read(repaired.read().image);
        assertTrue(Arrays.stream(result.getRGB(0, 0, 128, 128, null, 0, 128)).anyMatch(p -> p == 0xff228844));
    }

    @Test void onlyCancellationDuringShutdownIsQuiet() throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        List<java.util.logging.LogRecord> records = new CopyOnWriteArrayList<>();
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        Field log = findField(Log.class, "log"); log.setAccessible(true); Object previous = log.get(null); log.set(null, logger);
        try {
            for (boolean stopping : new boolean[]{false, true}) for (boolean cancellation : new boolean[]{false, true}) {
                Fixture f = new Fixture(); CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
                f.server.data = () -> {
                    reading.countDown(); await(release);
                    if (cancellation) throw new CancellationException("injected cancellation");
                    throw new IllegalStateException("injected real error");
                };
                Runnable job = f.update(new Tile(f.world, "0"));
                try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
                    Future<?> result = worker.submit(job); await(reading);
                    if (stopping) f.manager.beginShutdown();
                    records.clear(); release.countDown(); result.get(5, TimeUnit.SECONDS);
                }
                assertEquals(!stopping || !cancellation, records.stream().anyMatch(r -> r.getLevel() == java.util.logging.Level.SEVERE));
                assertEquals(1, f.manager.tileQueue.size());
            }
        } finally { log.set(null, previous); }
    }

    @Test void shutdownSavesZoomInvalidationPublishedByRunningWorker() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch rendering = new CountDownLatch(1), publish = new CountDownLatch(1);
        f.world.render = () -> { rendering.countDown(); await(publish); f.world.enqueueZoomOutUpdate(
                f.storage.getTile(f.world, f.map, 0, 0, 0, MapType.ImageVariant.STANDARD)); };
        Runnable job = f.update(new Tile(f.world, "0"));
        Class<?> poolType = Class.forName("org.dynmap.MapManager$DynmapScheduledThreadPoolExecutor");
        Constructor<?> ctor = poolType.getDeclaredConstructor(MapManager.class); ctor.setAccessible(true);
        ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) ctor.newInstance(f.manager);
        set(f.manager, "render_pool", pool);
        try (ExecutorService shutdown = Executors.newSingleThreadExecutor()) {
            pool.execute(job); await(rendering);
            Future<?> stopped = shutdown.submit(f.manager::stopRendering);
            try {
                await(f.world.unloaded);
                // In the old order shutdown() is reached only after savePending().
                // Release publication after this boundary, not merely after world unload.
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
                    while (!pool.isShutdown()) Thread.yield();
                });
            } finally { publish.countDown(); }
            stopped.get(5, TimeUnit.SECONDS);
        } finally { publish.countDown(); pool.shutdownNow(); }
        assertNotNull(f.saved().getNode("invZoomOut"));
        Fixture restarted = new Fixture(); restarted.manager.loadWorld(restarted.world);
        assertNotNull(restarted.world.mapstate.getFirst().saveZoomOut());
    }

    @Test void interruptedZoomPassSurvivesNextPassAndRestart() throws Exception {
        Fixture f = new Fixture(); MapTypeState state = f.world.mapstate.getFirst();
        state.setZoomOutInv(3, -1, 0); state.startZoomOutIter();
        // A cancelled pass never reaches this entry. Starting another pass must retain it.
        state.startZoomOutIter();
        assertNotNull(state.saveZoomOut());
        f.save(); Fixture restarted = new Fixture(); restarted.manager.loadWorld(restarted.world);
        assertNotNull(restarted.world.mapstate.getFirst().saveZoomOut());
    }

    @Test void failedZoomPublicationRemainsDirtyAndIsRetried() throws Exception {
        Fixture f = new Fixture();
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xff228844); image.setRGB(0, 0, 128, 128, pixels, 0, 128);
        MapStorageTile base = f.storage.getTile(f.world, f.map, 0, 0, 0, MapType.ImageVariant.STANDARD);
        assertTrue(base.write(5, image, 1));
        f.storage.failWrite = true;
        assertThrows(IllegalStateException.class, f.world::freshenZoomOutFiles);
        assertNotNull(f.world.mapstate.getFirst().saveZoomOut());
        f.storage.failWrite = false; f.world.freshenZoomOutFiles();
        assertTrue(base.getZoomOutTile().exists());
        BufferedImage repaired = ImageIO.read(new ByteArrayInputStream(f.storage.images.values().stream()
                .filter(i -> i.hash != 5).findFirst().orElseThrow().bytes));
        assertNotNull(repaired);
    }

    @Test void fullRenderRepairsStaleZoomWithoutRewritingUnchangedBase() throws Exception {
        Fixture f = new Fixture();
        MapStorageTile base = f.storage.getTile(f.world, f.map, 0, 0, 0, MapType.ImageVariant.STANDARD);
        BufferedImage good = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xff228844); good.setRGB(0, 0, 128, 128, pixels, 0, 128);
        base.write(7, good, 1); f.world.freshenZoomOutFiles(); f.world.freshenZoomOutFiles();
        MapStorageTile zoom = base.getZoomOutTile();
        BufferedImage black = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        zoom.write(0, black, 2);
        f.world.render = () -> assertTrue(base.matchesHashCode(7)); // unchanged base, as in the renderer's hash fast path
        int before = f.storage.writes;
        f.full(new Tile(f.world, "0")).run();
        assertEquals(before, f.storage.writes, "full render need not rewrite unchanged base data");
        assertNotNull(f.world.mapstate.getFirst().saveZoomOut(), "unchanged base must still request zoom repair");
        f.world.freshenZoomOutFiles();
        assertFalse(zoom.matchesHashCode(0));
    }

    record RenderInput(IsoHDPerspective perspective, HDMap map, HDMapTile tile, Cache cache) { }

    RenderInput renderer(Fixture f, int inclination, boolean failRay) throws Exception {
        ConfigurationNode config = new ConfigurationNode();
        config.put("name", "publication-test"); config.put("inclination", inclination); config.put("scale", 1);
        IsoHDPerspective perspective = new IsoHDPerspective(f.core, config);
        f.manager.hdmapman.perspectives.put("publication-test", perspective);
        HDShader shader = (HDShader) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HDShader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "publication-test";
                    case "getStateInstance" -> Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HDShaderState.class},
                            (state, m, a) -> switch (m.getName()) {
                                case "getMap" -> args[0];
                                case "getLighting" -> ((HDMap) args[0]).getLighting();
                                case "getShader" -> proxy;
                                case "processBlock" -> {
                                    if (failRay) throw new IllegalStateException("injected raytracing failure");
                                    yield true;
                                }
                                case "getRayColor" -> { ((Color) a[0]).setARGB(0xff228844); yield null; }
                                default -> null;
                            });
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        f.manager.hdmapman.shaders.put("publication-test", shader);
        config.put("shader", "publication-test"); config.put("perspective", "publication-test");
        config.put("image-format", "png");
        HDMap map = new HDMap(f.core, config);
        f.world.maps.clear(); f.world.maps.add(map); f.world.mapstate.clear();
        f.world.mapstate.add(new MapTypeState(f.world, map));
        HDMapTile tile = new HDMapTile(f.world, perspective, 0, 0, 0, 0);
        Cache cache = new Cache(); cache.setChunks(f.world, tile.getRequiredChunks()); cache.failRay = failRay;
        GenericChunk[] chunks = (GenericChunk[]) field(cache, "snaparray");
        GenericChunk chunk = new GenericChunk.Builder(0, 16).build();
        Arrays.fill(chunks, chunk);
        return new RenderInput(perspective, map, tile, cache);
    }

    @ParameterizedTest @ValueSource(ints = {90, 30})
    void flatAndSurfaceNeverPublishAnImageAfterRayFailure(int inclination) throws Exception {
        Fixture f = new Fixture(); RenderInput input = renderer(f, inclination, true);
        assertThrows(IllegalStateException.class, () -> input.perspective.render(input.cache, input.tile, null));
        assertEquals(0, f.storage.writes);
    }

    @ParameterizedTest @ValueSource(ints = {90, 30})
    void flatAndSurfacePropagateFailedPublicationAndCanRetry(int inclination) throws Exception {
        Fixture f = new Fixture(); RenderInput input = renderer(f, inclination, false);
        f.storage.failWrite = true;
        assertThrows(IllegalStateException.class, () -> input.perspective.render(input.cache, input.tile, null));
        assertEquals(0, f.storage.writes);
        f.storage.failWrite = false;
        assertTrue(input.perspective.render(input.cache, input.tile, null));
        assertEquals(1, f.storage.writes);
        assertFalse(input.perspective.render(input.cache, input.tile, null), "identical base keeps its original hash fast path");
    }

    @ParameterizedTest @ValueSource(ints = {90, 30})
    void mariaDbWriteFailurePropagatesThroughActualRenderer(int inclination) throws Exception {
        Fixture f = new Fixture(); RenderInput input = renderer(f, inclination, false);
        var storage = new org.dynmap.storage.mariadb.MariaDBMapStorage();
        @SuppressWarnings("unchecked") Map<String, Integer> keys = (Map<String, Integer>) field(storage, "mapKey");
        keys.put(f.world.getName() + ":" + input.map.getPrefix() + ":" + MapType.ImageVariant.STANDARD, 1);
        int[] attemptedWrites = {0};
        ResultSet empty = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> m.getName().equals("next") ? false : null);
        Statement query = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                (p, m, a) -> m.getName().equals("executeQuery") ? empty : null);
        PreparedStatement write = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (p, m, a) -> {
                    if (m.getName().equals("executeUpdate")) { attemptedWrites[0]++; throw new SQLException("injected MariaDB write failure", "HY000"); }
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> switch (m.getName()) {
                    case "createStatement" -> query;
                    case "prepareStatement" -> write;
                    default -> null;
                });
        ((Connection[]) field(storage, "cpool"))[0] = connection;
        ((long[]) field(storage, "cpoolLastUseTS"))[0] = System.currentTimeMillis();
        set(storage, "cpoolCount", 1);
        Field worldStorage = DynmapWorld.class.getDeclaredField("storage"); worldStorage.setAccessible(true); worldStorage.set(f.world, storage);
        assertThrows(IllegalStateException.class, () -> input.perspective.render(input.cache, input.tile, null));
        assertEquals(1, attemptedWrites[0], "must execute MariaDB's inherited SQL write path");
    }

    @Test void interruptedMariaDbConnectionWaitIsCancellationNotAMissingTile() throws Exception {
        Fixture f = new Fixture();
        var storage = new org.dynmap.storage.mariadb.MariaDBMapStorage();
        @SuppressWarnings("unchecked") Map<String, Integer> keys = (Map<String, Integer>) field(storage, "mapKey");
        keys.put(f.world.getName() + ":" + f.map.getPrefix() + ":" + MapType.ImageVariant.STANDARD, 1);
        MapStorageTile tile = storage.getTile(f.world, f.map, 0, 0, 0, MapType.ImageVariant.STANDARD);
        set(storage, "cpoolCount", 5); // All five production pool connections are held by other jobs.
        var thread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> result = worker.submit(() -> {
                thread.set(Thread.currentThread());
                assertThrows(CancellationException.class, tile::read);
                assertTrue(Thread.currentThread().isInterrupted());
            });
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
                while (thread.get() == null || thread.get().getState() != Thread.State.WAITING) Thread.yield();
            });
            thread.get().interrupt();
            result.get(5, TimeUnit.SECONDS);
        }
    }
}
