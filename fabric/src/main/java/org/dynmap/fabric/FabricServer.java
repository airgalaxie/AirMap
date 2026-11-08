package org.dynmap.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.fabric.event.BlockEvents;
import org.dynmap.fabric.event.ServerChatEvents;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Server access abstraction class
 */
public class FabricServer extends DynmapServerInterface {
    /* Server thread scheduler */
    private final FabricTaskQueue tasks = new FabricTaskQueue();
    private final FabricChunkProvider<SerializableChunkData> chunkProvider;
    private final DynmapPlugin plugin;
    private final MinecraftServer server;
    private final Registry<Biome> biomeRegistry;
    private long cur_tick;
    private long cur_tick_starttime;

    public FabricServer(DynmapPlugin plugin, MinecraftServer server) {
        this.plugin = plugin;
        this.server = server;
        this.chunkProvider = new FabricChunkProvider<>(server::isSameThread, System::nanoTime);
        this.biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
    }

    private NameAndId getProfileByName(String player) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(player);
        if (online != null) {
            return online.nameAndId();
        }
        return NameAndId.createOffline(player);
    }

    public final Registry<Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    private Biome[] biomelist = null;

    public final Biome[] getBiomeList(Registry<Biome> biomeRegistry) {
        if (biomelist == null) {
            biomelist = new Biome[256];
            Iterator<Biome> iter = biomeRegistry.iterator();
            while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = biomeRegistry.getId(b);
                if (bidx >= biomelist.length) {
                    biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
                }
                biomelist[bidx] = b;
            }
        }
        return biomelist;
    }

    public int getBlockIDAt(String wname, int x, int y, int z) {
        return -1;
    }

    @SuppressWarnings("deprecation") /* Not much I can do... fix this if it breaks. */
	@Override
    public int isSignAt(String wname, int x, int y, int z) {
        Level world = plugin.getLevelByName(wname).getLevel();

        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isLoaded(pos))
            return -1;

        Block block = world.getBlockState(pos).getBlock();
        return (block instanceof SignBlock ? 1 : 0);
    }

    @Override
    public void scheduleServerTask(Runnable run, long delay) {
        tasks.submit(Executors.callable(run, null), delay);
    }

    @Override
    public DynmapPlayer[] getOnlinePlayers() {
        if (server.getPlayerList() == null) return new DynmapPlayer[0];

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int playerCount = players.size();
        DynmapPlayer[] dplay = new DynmapPlayer[players.size()];

        for (int i = 0; i < playerCount; i++) {
            ServerPlayer player = players.get(i);
            dplay[i] = plugin.getOrAddPlayer(player);
        }

        return dplay;
    }

    @Override
    public void reload() {
        plugin.onDisable();
        plugin.onEnable();
        plugin.onStart();
    }

    @Override
    public DynmapPlayer getPlayer(String name) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer player : players) {

            if (player.getName().getString().equalsIgnoreCase(name)) {
                return plugin.getOrAddPlayer(player);
            }
        }

        return null;
    }

    @Override
    public Set<String> getIPBans() {
        IpBanList bl = server.getPlayerList().getIpBans();
        Set<String> ips = new HashSet<String>();

        for (String s : bl.getUserList()) {
            ips.add(s);
        }

        return ips;
    }

    @Override
    public <T> Future<T> callSyncMethod(Callable<T> task) {
        return callSyncMethod(task, 0);
    }

    public <T> Future<T> callSyncMethod(Callable<T> task, long delay) {
        return tasks.submit(task, delay);
    }

    void clearTaskQueue() {
        tasks.close();
        chunkProvider.close();
    }

    FabricChunkProvider<SerializableChunkData> chunkProvider() {
        return chunkProvider;
    }

    @Override
    public String getServerName() {
        String sn;
        if (server.isSingleplayer())
            sn = "Integrated";
        else
            sn = server.getLocalIp();
        if (sn == null) sn = "Unknown Server";
        return sn;
    }

    @Override
    public boolean isPlayerBanned(String pid) {
        PlayerList scm = server.getPlayerList();
        UserBanList bl = scm.getBans();
        try {
            return bl.isBanned(getProfileByName(pid));
        } catch (NoSuchElementException e) {
            /* If this profile doesn't exist, default to "banned" for good measure. */
            return true;
        }
    }

    @Override
    public String stripChatColor(String s) {
        return DynmapPlugin.patternControlCode.matcher(s).replaceAll("");
    }

    private Set<DynmapListenerManager.EventType> registered = new HashSet<DynmapListenerManager.EventType>();

    @Override
    public boolean requestEventNotification(DynmapListenerManager.EventType type) {
        if (registered.contains(type)) {
            return true;
        }

        switch (type) {
            case WORLD_LOAD:
            case WORLD_UNLOAD:
                /* Already called for normal world activation/deactivation */
                break;

            case WORLD_SPAWN_CHANGE:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onSpawnChange(SpawnChangeEvent evt) {
                        DynmapWorld w = new BukkitLevel(evt.getLevel());
                        core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                    }
                }, DynmapPlugin.this);
                */
                break;

            case PLAYER_JOIN:
            case PLAYER_QUIT:
                /* Already handled */
                break;

            case PLAYER_BED_LEAVE:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                        DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                        core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                    }
                }, DynmapPlugin.this);
                */
                break;

            case PLAYER_CHAT:
                if (plugin.chathandler == null) {
                    plugin.setChatHandler(new DynmapPlugin.ChatHandler(plugin));
                    ServerChatEvents.EVENT.register((player, message) -> plugin.chathandler.handleChat(player, message));
                }
                break;

            case BLOCK_BREAK:
                /* Already handled by BlockEvents logic */
                break;

            case SIGN_CHANGE:
                BlockEvents.SIGN_CHANGE_EVENT.register((world, pos, lines, player, front) -> {
                    plugin.core.processSignChange("fabric", FabricWorld.getLevelName(world),
                            pos.getX(), pos.getY(), pos.getZ(), lines, player.getName().getString());
                });
                break;

            default:
                Log.severe("Unhandled event type: " + type);
                return false;
        }

        registered.add(type);
        return true;
    }

    public boolean sendWebChatEvent(String source, String name, String msg) {
        return true;
    }

    @Override
    public void broadcastMessage(String msg) {
        Component component = Component.literal(msg);
        server.getPlayerList().broadcastSystemMessage(component, false);
        Log.info(stripChatColor(msg));
    }

    public String[] getBiomeIDs() {
        BiomeMap[] b = BiomeMap.values();
        String[] bname = new String[b.length];

        for (int i = 0; i < bname.length; i++) {
            bname[i] = b[i].toString();
        }

        return bname;
    }

    @Override
    public double getCacheHitRate() {
        if (plugin.sscache != null)
            return plugin.sscache.getHitRate();
        return 0.0;
    }

    @Override
    public void resetCacheStats() {
        if (plugin.sscache != null)
            plugin.sscache.resetStats();
    }

    @Override
    public DynmapWorld getWorldByName(String wname) {
        return plugin.getLevelByName(wname);
    }

    @Override
    public DynmapPlayer getOfflinePlayer(String name) {
        /*
        OfflinePlayer op = getServer().getOfflinePlayer(name);
        if(op != null) {
            return new BukkitPlayer(op);
        }
        */
        return null;
    }

    public Set<String> checkPlayerPermissions(String player, Set<String> perms) {
        if (isPlayerBanned(player)) {
            return Collections.emptySet();
        }
        Set<String> rslt = plugin.hasOfflinePermissions(player, perms);
        if (rslt == null) {
            rslt = new HashSet<String>();
            if (plugin.isOp(player)) {
                rslt.addAll(perms);
            }
        }
        return rslt;
    }

    @Override
    public boolean checkPlayerPermission(String player, String perm) {
        if (isPlayerBanned(player)) {
            return false;
        }
        return plugin.hasOfflinePermission(player, perm);
    }

    /**
     * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
     */
    @Override
    public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
                                             boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
        chunkProvider.requireWorker();
        FabricMapChunkCache c = (FabricMapChunkCache) w.getChunkCache(chunks);
        if (c == null) {
            return null;
        }
        c.requireCurrentWorld();
        if (w.visibility_limits != null) {
            for (VisibilityLimit limit : w.visibility_limits) {
                c.setVisibleRange(limit);
            }

            c.setHiddenFillStyle(w.hiddenchunkstyle);
        }

        if (w.hidden_limits != null) {
            for (VisibilityLimit limit : w.hidden_limits) {
                c.setHiddenRange(limit);
            }

            c.setHiddenFillStyle(w.hiddenchunkstyle);
        }

        if (!c.setChunkDataTypes(blockdata, biome, highesty, rawbiome)) {
            Log.severe("CraftBukkit build does not support biome APIs");
        }

        if (chunks.size() == 0)     /* No chunks to get? */ {
            c.loadChunksAsync();
            c.requireCurrentWorld();
            return c;
        }

        c.getLoadedChunksAsync();
        if (!w.isLoaded()) {
            return null;
        }
        // Now, do rest of chunk reading from calling thread
        c.readChunksAsync();
        c.requireCurrentWorld();
        return c;
    }

    @Override
    public int getMaxPlayers() {
        return server.getPlayerList().getMaxPlayers();
    }

    @Override
    public int getCurrentPlayers() {
        return server.getPlayerList().getPlayerCount();
    }

    public void tickEvent(MinecraftServer server) {
        cur_tick_starttime = System.nanoTime();
        long elapsed = cur_tick_starttime - plugin.lasttick;
        plugin.lasttick = cur_tick_starttime;
        plugin.avgticklen = ((plugin.avgticklen * 99) / 100) + (elapsed / 100);
        plugin.tps = (double) 1E9 / (double) plugin.avgticklen;
        // Tick core
        if (plugin.core != null) {
            plugin.core.serverTick(plugin.tps);
        }

        while (!plugin.blockupdatequeue.isEmpty()) {
            DynmapPlugin.BlockUpdateRec r = plugin.blockupdatequeue.remove();
            BlockState bs = r.w.getBlockState(new BlockPos(r.x, r.y, r.z));
            int idx = Block.BLOCK_STATE_REGISTRY.getId(bs);
            if (!org.dynmap.hdmap.HDBlockModels.isChangeIgnoredBlock(DynmapPlugin.stateByID[idx])) {
                if (plugin.onblockchange_with_id)
                    plugin.mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange[" + idx + "]");
                else
                    plugin.mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange");
            }
        }

        // Refresh both copy limits from the shared controller on every tick.
        long remainingTickBudget = plugin.perTickLimit - (System.nanoTime() - cur_tick_starttime);
        if (plugin.mapManager != null && remainingTickBudget > 0) {
            chunkProvider.processTick(Math.max(1, plugin.mapManager.getMaxChunkLoadsPerTick()),
                    Math.min(remainingTickBudget, plugin.mapManager.getRenderDataBudgetNanos()));
        }
        cur_tick++;
        tasks.nextTick();
        while (System.nanoTime() - cur_tick_starttime <= plugin.perTickLimit) {
            TaskRecord task = tasks.poll();
            if (task == null) break;
            tasks.run(task);
        }
        while (!plugin.msgqueue.isEmpty()) {
            DynmapPlugin.ChatMessage cm = plugin.msgqueue.poll();
            DynmapPlayer dp = null;
            if (cm.sender != null)
                dp = plugin.getOrAddPlayer(cm.sender);
            else
                dp = new FabricPlayer(plugin, null);

            plugin.core.listenerManager.processChatEvent(DynmapListenerManager.EventType.PLAYER_CHAT, dp, cm.message);
        }
        // Check for generated chunks
        if ((cur_tick % 20) == 0) {
        }
    }

    private Optional<ModContainer> getModContainerById(String id) {
        return FabricLoader.getInstance().getModContainer(id);
    }

    public boolean isModLoaded(String name) {
        return FabricLoader.getInstance().getModContainer(name).isPresent();
    }

    @Override
    public String getModVersion(String name) {
        Optional<ModContainer> mod = getModContainerById(name);    // Try case sensitive lookup
        return mod.map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    public double getServerTPS() {
        return plugin.tps;
    }

    @Override
    public String getServerIP() {
        if (server.isSingleplayer())
            return "0.0.0.0";
        else
            return server.getLocalIp();
    }

    @Override
    public File getModContainerFile(String name) {
        Optional<ModContainer> container = getModContainerById(name);    // Try case sensitive lookup
        if (container.isPresent()) {
            Path path = container.get().getRootPath();
            if (path.getFileSystem().provider().getScheme().equals("jar")) {
                path = Paths.get(path.getFileSystem().toString());
            }
            return path.toFile();
        }
        return null;
    }

    @Override
    public List<String> getModList() {
        return FabricLoader.getInstance()
                .getAllMods()
                .stream()
                .map(container -> container.getMetadata().getId())
                .collect(Collectors.toList());
    }

    public Map<Integer, String> getBlockIDMap() {
        Map<Integer, String> map = new HashMap<Integer, String>();
        return map;
    }

    @Override
    public InputStream openResource(String modid, String rname) {
        if (modid == null) modid = "minecraft";

        if ("minecraft".equals(modid)) {
            return MinecraftServer.class.getClassLoader().getResourceAsStream(rname);
        } else {
            if (rname.startsWith("/") || rname.startsWith("\\")) {
                rname = rname.substring(1);
            }

            final String finalModid = modid;
            final String finalRname = rname;
            return getModContainerById(modid).map(container -> {
                try {
                    return Files.newInputStream(container.getPath(finalRname));
                } catch (IOException e) {
                    Log.severe("Failed to load resource of mod :" + finalModid, e);
                    return null;
                }
            }).orElse(null);
        }
    }

    /**
     * Get block unique ID map (module:blockid)
     */
    @Override
    public Map<String, Integer> getBlockUniqueIDMap() {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        return map;
    }

    /**
     * Get item unique ID map (module:itemid)
     */
    @Override
    public Map<String, Integer> getItemUniqueIDMap() {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        return map;
    }

}
