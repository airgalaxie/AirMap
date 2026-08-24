package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import org.dynmap.DynmapCore;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.DynmapWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.resources.MinecraftClientResources;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Loads the default texture pack against the real client resources and verifies biome grass shading colors. */
class TexturePackGrassColorTest {
    private static final int IMG_GRASSCOLOR = 0;
    private static final int IMG_FOLIAGECOLOR = 1;

    @Test
    void forestGrassColormapColorIsGreen() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(MinecraftClientResources.configuredVersion());
        core.setServer(new DynmapServerInterface() {
            @Override public void scheduleServerTask(Runnable run, long delay) { }
            @Override public <T> java.util.concurrent.Future<T> callSyncMethod(java.util.concurrent.Callable<T> task) { return null; }
            @Override public DynmapPlayer[] getOnlinePlayers() { return new DynmapPlayer[0]; }
            @Override public void reload() { }
            @Override public DynmapPlayer getPlayer(String name) { return null; }
            @Override public DynmapPlayer getOfflinePlayer(String name) { return null; }
            @Override public java.util.Set<String> getIPBans() { return java.util.Set.of(); }
            @Override public String getServerName() { return "test"; }
            @Override public boolean isPlayerBanned(String pid) { return false; }
            @Override public String stripChatColor(String s) { return s; }
            @Override public boolean requestEventNotification(EventType type) { return false; }
            @Override public void broadcastMessage(String msg) { }
            @Override public double getCacheHitRate() { return 0; }
            @Override public void resetCacheStats() { }
            @Override public DynmapWorld getWorldByName(String wname) { return null; }
            @Override public boolean checkPlayerPermission(String player, String perm) { return false; }
            @Override public MapChunkCache createMapChunkCache(DynmapWorld w, java.util.List<DynmapChunk> chunks,
                    boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) { return null; }
            @Override public int getMaxPlayers() { return 0; }
            @Override public int getCurrentPlayers() { return 0; }
            @Override public int isSignAt(String wname, int x, int y, int z) { return 0; }
            @Override public String getServerIP() { return "127.0.0.1"; }
        });

        HDBlockStateTextureMap.initializeTable();
        TexturePack tp = TexturePack.getTexturePack(core, "standard");
        assertNotNull(tp, "default texture pack failed to load");

        int[] grass = argb(tp, IMG_GRASSCOLOR);
        int[] foliage = argb(tp, IMG_FOLIAGECOLOR);
        assertNotNull(grass, "grass colormap was not loaded");
        assertNotNull(foliage, "foliage colormap was not loaded");
        assertTrue(isLoaded(tp, IMG_GRASSCOLOR), "grass colormap flagged as not loaded");

        assertGreenish("forest grass", forestMultiplier(grass));
        assertGreenish("forest foliage", forestMultiplier(foliage));
    }

    private static int forestMultiplier(int[] colormap) {
        return colormap[BiomeMap.FOREST.biomeLookup()];
    }

    private static void assertGreenish(String what, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertTrue(g > r && g > b && g > 60,
                what + " color is not green: RGB=(" + r + "," + g + "," + b + ")");
    }

    private static int[] argb(TexturePack tp, int index) throws Exception {
        Field imgs = TexturePack.class.getDeclaredField("imgs");
        imgs.setAccessible(true);
        Object[] loaded = (Object[]) imgs.get(tp);
        Field argb = loaded[index].getClass().getDeclaredField("argb");
        argb.setAccessible(true);
        return (int[]) argb.get(loaded[index]);
    }

    private static boolean isLoaded(TexturePack tp, int index) throws Exception {
        Field imgs = TexturePack.class.getDeclaredField("imgs");
        imgs.setAccessible(true);
        Object[] loaded = (Object[]) imgs.get(tp);
        Field flag = loaded[index].getClass().getDeclaredField("isLoaded");
        flag.setAccessible(true);
        return flag.getBoolean(loaded[index]);
    }

    private static Object defaultReturn(Class<?> type) {
        if (type == boolean.class) return false;
        if (type.isPrimitive()) return 0;
        return null;
    }
}
