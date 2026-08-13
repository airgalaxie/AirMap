package org.dynmap.bukkit;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.bukkit.permissions.BukkitPermissions;
import org.dynmap.bukkit.permissions.GroupManagerPermissions;
import org.dynmap.bukkit.permissions.PermissionProvider;
import org.dynmap.bukkit.permissions.VaultPermissions;
import org.dynmap.bukkit.permissions.LuckPerms5Permissions;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.modsupport.ModSupportImpl;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;
import org.dynmap.utils.VisibilityLimit;

@SuppressWarnings("UnstableApiUsage")
public class DynmapPlugin extends JavaPlugin implements DynmapAPI {
    DynmapCore core;
    private PermissionProvider permissions;
    private String version;
    public PlayerList playerList;
    private MapManager mapManager;
    public static DynmapPlugin plugin;
    public PluginManager pm;
    private HashMap<String, BukkitWorld> world_by_name = new HashMap<String, BukkitWorld>();

    private long cur_tick;
    private long prev_tick;

    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
    /* Lookup cache */
    private World last_world;
    private BukkitWorld last_bworld;
    
    private BukkitVersionHelper helper;

    private final BukkitWorld getWorldByName(String name) {
        if((last_world != null) && (last_bworld.getName().equals(name)
                || last_bworld.getNameAliases().contains(name))) {
            return last_bworld;
        }
        BukkitWorld world = world_by_name.get(name);
        if (world != null) {
            return world;
        }
        for (BukkitWorld candidate : world_by_name.values()) {
            if (candidate.getNameAliases().contains(name)) {
                return candidate;
            }
        }
        return null;
    }
    private final BukkitWorld getWorld(World w) {
        if(last_world == w) {
            return last_bworld;
        }
        String worldId = w.getKey().toString();
        BukkitWorld bw = world_by_name.get(worldId);
        if(bw == null) {
            bw = new BukkitWorld(w);
            world_by_name.put(worldId, bw);
        }
        else if(bw.isLoaded() == false) {
            bw.setWorldLoaded(w);
        }
        last_world = w;
        last_bworld = bw;

        return bw;
    }
    final void removeWorld(World w) {
        world_by_name.remove(w.getKey().toString());
        if(w == last_world) {
            last_world = null;
            last_bworld = null;
        }
    }

    private static class BlockToCheck {
        Location loc;
        Material type;
        String trigger;
    };
    private LinkedList<BlockToCheck> blocks_to_check = null;
    private LinkedList<BlockToCheck> blocks_to_check_accum = new LinkedList<BlockToCheck>();
    
    public DynmapPlugin() {
        plugin = this;
    }

    /**
     * Server access abstraction class
     */
    public class BukkitServer extends DynmapServerInterface {
        @Override
        public int isSignAt(String wname, int x, int y, int z) {
            World w = getServer().getWorld(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                Block b = w.getBlockAt(x, y, z);
                BlockState s = b.getState();

                if (s instanceof Sign) {
                    return 1;
                } else {
                    return 0;
                }
            }
            return -1;
        }

        @Override
        public void scheduleServerTask(Runnable run, long delay) {
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, run, delay);
        }
        @Override
        public DynmapPlayer[] getOnlinePlayers() {
            Player[] players = helper.getOnlinePlayers();
            DynmapPlayer[] dplay = new DynmapPlayer[players.length];
            for(int i = 0; i < players.length; i++)
                dplay[i] = new BukkitPlayer(players[i]);
            return dplay;
        }
        @Override
        public void reload() {
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.disablePlugin(DynmapPlugin.this);
            pluginManager.enablePlugin(DynmapPlugin.this);
        }
        @Override
        public DynmapPlayer getPlayer(String name) {
            Player p = getServer().getPlayerExact(name);
            if(p != null) {
                return new BukkitPlayer(p);
            }
            return null;
        }
        @Override
        public Set<String> getIPBans() {
            return getServer().getIPBans();
        }
        @Override
        public <T> Future<T> callSyncMethod(Callable<T> task) {
            if(DynmapPlugin.this.isEnabled())
                return getServer().getScheduler().callSyncMethod(DynmapPlugin.this, task);
            else
                return null;
        }
        @Override
        public String getServerName() {
    		return getServer().getMotd();
        }
        @Override
        public boolean isPlayerBanned(String pid) {
            OfflinePlayer p = getServer().getOfflinePlayer(pid);
            if((p != null) && p.isBanned())
                return true;
            return false;
        }
        @Override
        public String stripChatColor(String s) {
            return ChatColor.stripColor(s);
        }
        private Set<EventType> registered = new HashSet<EventType>();
        @Override
        public boolean requestEventNotification(EventType type) {
            if(registered.contains(type))
                return true;
            switch(type) {
                case WORLD_LOAD:
                case WORLD_UNLOAD:
                    /* Already called for normal world activation/deactivation */
                    break;
                case WORLD_SPAWN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onSpawnChange(SpawnChangeEvent evt) {
                            BukkitWorld w = getWorld(evt.getWorld());
                            core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                    /* Already handled */
                    break;
                case PLAYER_BED_LEAVE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_CHAT:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onPlayerChat(AsyncPlayerChatEvent evt) {
                            final Player p = evt.getPlayer();
                            final String msg = evt.getMessage();
                            getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, new Runnable() {
                                public void run() {
                                    DynmapPlayer dp = null;
                                    if(p != null)
                                        dp = new BukkitPlayer(p);
                                    core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, dp, msg);
                                }
                            });
                        }
                    }, DynmapPlugin.this);
                    break;
                case BLOCK_BREAK:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            Block b = evt.getBlock();
                            if(b == null) return;   /* Work around for stupid mods.... */
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().name(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }, DynmapPlugin.this);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
                        public void onSignChange(SignChangeEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();    /* Note: changes to this change event - intentional */
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if(p != null) dp = new BukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().name(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), lines, dp);

                            for (int i = 0; i < 4; i++) {
                                evt.setLine(i, lines[i]);
                            }
                        }
                    }, DynmapPlugin.this);
                    break;
                default:
                    Log.severe("Unhandled event type: " + type);
                    return false;
            }
            registered.add(type);
            return true;
        }
        @Override
        public void broadcastMessage(String msg) {
            getServer().broadcastMessage(msg);
        }
        @Override
        public double getCacheHitRate() {
        	return BukkitVersionHelper.gencache.getHitRate();
        }
        @Override
        public void resetCacheStats() {
        	BukkitVersionHelper.gencache.resetStats();
        }
        @Override
        public DynmapWorld getWorldByName(String wname) {
            return DynmapPlugin.this.getWorldByName(wname);
        }
        @Override
        public DynmapPlayer getOfflinePlayer(String name) {
            OfflinePlayer op = getServer().getOfflinePlayer(name);
            if(op != null) {
                return new BukkitPlayer(op);
            }
            return null;
        }
        @Override
        public boolean checkPlayerPermission(String player, String perm) {
            OfflinePlayer p = getServer().getOfflinePlayer(player);
            if(p.isBanned())
                return false;
            boolean rslt = permissions.hasOfflinePermission(player, perm);
            return rslt;
        }
        /**
         * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
         */
        @Override
        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks, 
                boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
            MapChunkCache c = w.getChunkCache(chunks);
            if(c == null) { /* Can fail if not currently loaded */
                return null;
            }
            if(w.visibility_limits != null) {
                for(VisibilityLimit limit: w.visibility_limits) {
                    c.setVisibleRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if(w.hidden_limits != null) {
                for(VisibilityLimit limit: w.hidden_limits) {
                    c.setHiddenRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if(c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false) {
                Log.severe("CraftBukkit build does not support biome APIs");
            }
            if(chunks.size() == 0) {    /* No chunks to get? */
                ((GenericMapChunkCache) c).loadChunksAsync();
                return c;
            }

			while(!c.isDoneLoading()) {
                if (prev_tick != cur_tick) {
                    prev_tick = cur_tick;
                }

                ((GenericMapChunkCache) c).loadChunksAsync();
            }
            /* If cancelled due to world unload return nothing */
            if(w.isLoaded() == false)
                return null;
            return c;
        }
        @Override
        public int getMaxPlayers() {
            return getServer().getMaxPlayers();
        }
        @Override
        public int getCurrentPlayers() {
            return helper.getOnlinePlayers().length;
        }
        @Override
        public String getServerIP() {
            return Bukkit.getServer().getIp();
        }
    }
    /**
     * Player access abstraction class
     */
    public class BukkitPlayer extends BukkitCommandSender implements DynmapPlayer {
        private Player player;
        private OfflinePlayer offplayer;
        private String skinurl;
        private UUID uuid;
        
        public BukkitPlayer(Player p) {
            super(p);
            player = p;
            offplayer = p.getPlayer();
            uuid = p.getUniqueId();
            skinurl = helper.getSkinURL(p);
        }
        public BukkitPlayer(OfflinePlayer p) {
            super(null);
            offplayer = p;
        }
        @Override
        public boolean isConnected() {
            return offplayer.isOnline();
        }
        @Override
        public String getName() {
            return offplayer.getName();
        }
        @Override
        public String getDisplayName() {
            if(player != null)
                return player.getDisplayName();
            else
                return offplayer.getName();
        }
        @Override
        public boolean isOnline() {
            return offplayer.isOnline();
        }
        @Override
        public DynmapLocation getLocation() {
            if(player == null) {
                return null;
            }
            Location loc = player.getEyeLocation(); // Use eye location, since we show head 
            return toLoc(loc);
        }
        @Override
        public String getWorld() {
            if(player == null) {
                return null;
            }
            World w = player.getWorld();
            if(w != null)
                return DynmapPlugin.this.getWorld(w).getName();
            return null;
        }
        @Override
        public InetSocketAddress getAddress() {
            if(player != null)
                return player.getAddress();
            return null;
        }
        @Override
        public boolean isSneaking() {
            if(player != null)
                return player.isSneaking();
            return false;
        }
        @Override
        public double getHealth() {
            if(player != null) {
            	return Math.ceil(2.0 * player.getHealth() / player.getAttribute(Attribute.MAX_HEALTH).getValue() * player.getHealthScale()) / 2.0;
            }
            else
                return 0;
        }
        @Override
        public int getArmorPoints() {
            if(player != null)
                return (int) player.getAttribute(Attribute.ARMOR).getValue();
            else
                return 0;
        }
        @Override
        public DynmapLocation getBedSpawnLocation() {
            Location loc = offplayer.getRespawnLocation();
            if(loc != null) {
                return toLoc(loc);
            }
            return null;
        }
        @Override
        public long getLastLoginTime() {
            return offplayer.getLastSeen();
        }
        @Override
        public long getFirstLoginTime() {
            return offplayer.getFirstPlayed();
        }
        @Override
        public boolean isInvisible() {
            if(player != null) {
                return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
            }
            return false;
        }
        @Override
        public boolean isVanished() {
            if(player != null) {
                return player.hasMetadata("vanished");
            }
            return false;
        }
        @Override
        public boolean isSpectator() {
          if(player != null) {
              return player.getGameMode() == GameMode.SPECTATOR;
          }
            return false;
        }
        @Override
        public int getSortWeight() {
            Integer wt = sortWeights.get(getName());
            if (wt != null)
                return wt;
            return 0;
        }
        @Override
        public void setSortWeight(int wt) {
            if (wt == 0) {
                sortWeights.remove(getName());
            }
            else {
                sortWeights.put(getName(), wt);
            }
        }
        @Override
        public String getSkinURL() {
        	return skinurl;
        }
        @Override
        public UUID getUUID() {
        	return uuid;
        }
        /**
         * Send title and subtitle text (called from server thread)
         */
        @Override
        public void sendTitleText(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
        	if (player != null) {
        		helper.sendTitleText(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
        	}
    	}
    }
    /* Handler for generic console command sender */
    public class BukkitCommandSender implements DynmapCommandSender {
        private CommandSender sender;

        public BukkitCommandSender(CommandSender send) {
            sender = send;
        }
        
        @Override
        public boolean hasPrivilege(String privid) {
            if(sender != null)
                return permissions.has(sender, privid);
            return false;
        }

        @Override
        public void sendMessage(String msg) {
            if(sender != null)
                sender.sendMessage(msg);
        }

        @Override
        public boolean isConnected() {
            if(sender != null)
                return true;
            return false;
        }
        @Override
        public boolean isOp() {
            if(sender != null)
                return sender.isOp();
            else
                return false;
        }
        @Override
        public boolean hasPermissionNode(String node) {
            if (sender != null) {
                return sender.hasPermission(node);
            }
            return false;
        }
    }
    
    public void loadExtraBiomes() {
        int cnt = 0;

        /* Find array of biomes in biomebase */
        Object[] biomelist = helper.getBiomeBaseList();
        //Log.info("biomelist length = " + biomelist.length);
        /* Loop through list, skipping well known biomes */
        for(int i = 0; i < biomelist.length; i++) {
            Object bb = biomelist[i];
            if(bb != null) {
            	String rl = helper.getBiomeBaseResourceLocsation(bb);
                float tmp = helper.getBiomeBaseTemperature(bb);
                float hum = helper.getBiomeBaseHumidity(bb);
                int watermult = helper.getBiomeBaseWaterMult(bb);
                Log.verboseinfo("biome[" + i + "]: hum=" + hum + ", tmp=" + tmp + ", mult=" + Integer.toHexString(watermult));
                
                BiomeMap bmap = BiomeMap.NULL;
                if (rl != null) {	// If resource location, lookup by this
                	bmap = BiomeMap.byBiomeResourceLocation(rl);
                }
                else {
                	bmap = BiomeMap.byBiomeID(i);
                }
                if (bmap.isDefault() || (bmap == BiomeMap.NULL)) {
                    String id =  helper.getBiomeBaseIDString(bb);
                    if (id == null) {
                        id = "BIOME_" + i;
                    }
                    bmap = new BiomeMap((rl != null) ? BiomeMap.NO_INDEX : i, id, tmp, hum, rl);
                    Log.verboseinfo("Add custom biome [" + bmap.toString() + "] (" + i + ") rl=" + rl);
                    //Log.info(String.format("rl=%s, bmap=%s", rl, bmap));
                    cnt++;
                }
                else {
                    bmap.setTemperature(tmp);
                    bmap.setRainfall(hum);
                }
                if (watermult != -1) {
                	bmap.setWaterColorMultiplier(watermult);
                	Log.verboseinfo("Set watercolormult for " + bmap.toString() + " (" + i + ") to " + Integer.toHexString(watermult));
                }
                bmap.setBiomeObject(bb);
            }
        }
        if(cnt > 0) {
            Log.info("Added " + cnt + " custom biome mappings");
        }
    }
    
    @Override
    public void onLoad() {
        Log.setLogger(this.getLogger(), "");
        
        helper = new BukkitVersionHelper();
        pm = this.getServer().getPluginManager();
        
        ModSupportImpl.init();
    }
    
    @Override
    public void onEnable() {
        if(core != null){
            if(core.getMarkerAPI() != null){
                getLogger().info("Starting Scheduled Write Job (markerAPI).");
                core.restartMarkerSaveJob();
            }
        }
        if (helper == null) {
            Log.info("Dynmap is disabled (unsupported platform)");
            this.setEnabled(false);
            return;
        }
        PluginMeta meta = this.getPluginMeta();
        version = meta.getVersion();

        /* Get MC version */
        String bukkitver = getServer().getVersion();
        String mcver = "1.0.0";
        int idx = bukkitver.indexOf("(MC: ");
        if(idx > 0) {
            mcver = bukkitver.substring(idx+5);
            idx = mcver.indexOf(")");
            if(idx > 0) mcver = mcver.substring(0, idx);
        }

        // Initialize block states
        helper.initializeBlockStates();
        
        /* Load extra biomes, if any */
        loadExtraBiomes();
             
        /* Set up player login/quit event handler */
        registerPlayerLoginListener();

        /* Build default permissions from our plugin */
        Map<String, Boolean> perdefs = new HashMap<>();
        List<Permission> pd = plugin.getPluginMeta().getPermissions();
        for(Permission p : pd) {
            perdefs.put(p.getName(), p.getDefault() == PermissionDefault.TRUE);
        }
        

        permissions = LuckPerms5Permissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = GroupManagerPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = VaultPermissions.create(this, "dynmap");
        if (permissions == null)
            permissions = BukkitPermissions.create("dynmap", perdefs);
        /* Get and initialize data folder */
        File dataDirectory = this.getDataFolder();
        if(dataDirectory.exists() == false)
            dataDirectory.mkdirs();
         
        /* Instantiate core */
        if(core == null)
            core = new DynmapCore();
        /* Inject dependencies */
        core.setPluginJarFile(this.getFile());
        core.setPluginVersion(version, "CraftBukkit");
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        core.setServer(new BukkitServer());
        core.setBiomeNames(helper.getBiomeNames());
        
        /* Load configuration */
        if(!core.initConfiguration(null)) {
            this.setEnabled(false);
            return;
        }

		LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            final CommandHandler mapCommand = new CommandHandler(this, "dynmap");
            commands.register("airmap", "Controls AirMap", mapCommand);
            commands.register("dynmap", "Controls Dynmap",
                              mapCommand);
            commands.register("dmarker", "Manipulate map markers",
                              new CommandHandler(this, "dmarker"));
            commands.register("dmap", "List and modify dynmap configuration",
                              new CommandHandler(this, "dmap"));
            commands.register("dynmapexp", "Map export commands",
                              new CommandHandler(this, "dynmapexp"));
        });

        doEnable();

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                processTick();
            }
        }, 1, 1);
    }

    private void doEnable() {
        /* Enable core */
        if(!core.enableCore(null)) {
            this.setEnabled(false);
            return;
        }
        playerList = core.playerList;
        BukkitVersionHelper.gencache = new GenericChunkCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());

        /* Get map manager from core */
        mapManager = core.getMapManager();
        /* Initialized the currently loaded worlds */
        for (World world : getServer().getWorlds()) {
            BukkitWorld w = getWorld(world);
            if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
        }    
        /* Register our update trigger events */
        registerEvents();

        /* Core is ready - notify API availability */
        DynmapCommonAPIListener.apiInitialized(this);

        Log.info("Enabled");
    }
    
    @Override
    public void onDisable() {
        /* Core is being disabled - notify API disable */
        DynmapCommonAPIListener.apiTerminated();

        /* Disable core */
	    if (core != null) {
		    core.disableCore();
	    }

        if (BukkitVersionHelper.gencache != null) {
        	BukkitVersionHelper.gencache.cleanup();
        	BukkitVersionHelper.gencache = null; 
        }
        Log.info("Disabled");
    }
    
    private void processTick() {
        cur_tick++;
        
        // Tick core
        if (core != null) {
            core.serverTick(getServer().getTPS()[0]);
        }
    }

    @Override
    public final MarkerAPI getMarkerAPI() {
        return core.getMarkerAPI();
    }

    @Override
    public final boolean markerAPIInitialized() {
        return core.markerAPIInitialized();
    }

    @Override
    public final boolean sendBroadcastToWeb(String sender, String msg) {
        return core.sendBroadcastToWeb(sender, msg);
    }

    @Override
    public final int triggerRenderOfVolume(String wid, int minx, int miny, int minz,
            int maxx, int maxy, int maxz) {
		invalidateSnapshot(wid, minx, miny, minz, maxx, maxy, maxz);
        return core.triggerRenderOfVolume(wid, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public final int triggerRenderOfBlock(String wid, int x, int y, int z) {
		invalidateSnapshot(wid, x, y, z);
        return core.triggerRenderOfBlock(wid, x, y, z);
    }

    @Override
    public final void setPauseFullRadiusRenders(boolean dopause) {
        core.setPauseFullRadiusRenders(dopause);
    }

    @Override
    public final boolean getPauseFullRadiusRenders() {
        return core.getPauseFullRadiusRenders();
    }

    @Override
    public final void setPauseUpdateRenders(boolean dopause) {
        core.setPauseUpdateRenders(dopause);
    }

    @Override
    public final boolean getPauseUpdateRenders() {
        return core.getPauseUpdateRenders();
    }

    @Override
    public final void setPlayerVisiblity(String player, boolean is_visible) {
        core.setPlayerVisiblity(player, is_visible);
    }

    @Override
    public final boolean getPlayerVisbility(String player) {
        return core.getPlayerVisbility(player);
    }

    @Override
    public final void postPlayerMessageToWeb(String playerid, String playerdisplay,
            String message) {
        core.postPlayerMessageToWeb(playerid, playerdisplay, message);
    }

    @Override
    public final void postPlayerJoinQuitToWeb(String playerid, String playerdisplay,
            boolean isjoin) {
        core.postPlayerJoinQuitToWeb(playerid, playerdisplay, isjoin);
    }

    @Override
    public final String getDynmapCoreVersion() {
        return core.getDynmapCoreVersion();
    }

    @Override
    public final int triggerRenderOfVolume(Location l0, Location l1) {
        int x0 = l0.getBlockX(), y0 = l0.getBlockY(), z0 = l0.getBlockZ();
        int x1 = l1.getBlockX(), y1 = l1.getBlockY(), z1 = l1.getBlockZ();
        
        return core.triggerRenderOfVolume(getWorld(l0.getWorld()).getName(), Math.min(x0, x1), Math.min(y0, y1),
                Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    @Override
    public final void setPlayerVisiblity(Player player, boolean is_visible) {
        core.setPlayerVisiblity(player.getName(), is_visible);
    }

    @Override
    public final boolean getPlayerVisbility(Player player) {
        return core.getPlayerVisbility(player.getName());
    }

    @Override
    public final void postPlayerMessageToWeb(Player player, String message) {
        core.postPlayerMessageToWeb(player.getName(), player.getDisplayName(), message);
    }

    @Override
    public void postPlayerJoinQuitToWeb(Player player, boolean isjoin) {
        core.postPlayerJoinQuitToWeb(player.getName(), player.getDisplayName(), isjoin);
    }

    @Override
    public String getDynmapVersion() {
        return version;
    }
    
    private static DynmapLocation toLoc(Location l) {
        return new DynmapLocation(plugin.getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
    
    private void registerPlayerLoginListener() {
        Listener pl = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onPlayerJoin(PlayerJoinEvent evt) {
                final DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                // Give other handlers a change to prep player (nicknames and such from Essentials)
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
                    }
                }, 2);
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onPlayerQuit(PlayerQuitEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
            }
        };
        pm.registerEvents(pl, this);
    }

    private class BlockCheckHandler implements Runnable {
        public void run() {
            BlockToCheck btt;
            while(blocks_to_check.isEmpty() != true) {
                btt = blocks_to_check.pop();
                Location loc = btt.loc;
                World w = loc.getWorld();
                if(!w.isChunkLoaded(loc.getBlockX()>>4, loc.getBlockZ()>>4))
                    continue;
                Material bt = w.getBlockAt(loc).getType();
                /* Avoid stationary and moving water churn */
                //if(bt == 9) bt = 8;
                //if(btt.typeid == 9) btt.typeid = 8;
                if(bt != btt.type) {
                    String wn = getWorld(w).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());                    	
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), btt.trigger);
                }
            }
            blocks_to_check = null;
            /* Kick next run, if one is needed */
            startIfNeeded();
        }
        public void startIfNeeded() {
            if((blocks_to_check == null) && (blocks_to_check_accum.isEmpty() == false)) { /* More pending? */
                blocks_to_check = blocks_to_check_accum;
                blocks_to_check_accum = new LinkedList<BlockToCheck>();
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, this, 10);
            }
        }
    }
    private BlockCheckHandler btth = new BlockCheckHandler();

    private void checkBlock(Block b, String trigger) {
        BlockToCheck btt = new BlockToCheck();
        btt.loc = b.getLocation();
        btt.type = b.getType();
        btt.trigger = trigger;
        blocks_to_check_accum.add(btt); /* Add to accumulator */
        btth.startIfNeeded();
    }
    
    private boolean onplace;
    private boolean onbreak;
    private boolean onblockform;
    private boolean onblockfade;
    private boolean onblockspread;
    private boolean onblockfromto;
    private boolean onblockphysics;
    private boolean onleaves;
    private boolean onburn;
    private boolean onpiston;
    private boolean onplayerjoin;
    private boolean onplayermove;
    private boolean ongeneratechunk;
    private boolean onexplosion;
    private boolean onstructuregrow;
    private boolean onblockgrow;
    private boolean onblockredstone;

    private void invalidateSnapshot(String wn, int x, int y, int z) {
    	BukkitVersionHelper.gencache.invalidateSnapshot(wn, x, y, z);
    }
    private void invalidateSnapshot(String wname, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
    	BukkitVersionHelper.gencache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
    }

    private void registerEvents() {
        
        // To trigger rendering.
        onplace = core.isTrigger("blockplaced");
        onbreak = core.isTrigger("blockbreak");
        onleaves = core.isTrigger("leavesdecay");
        onburn = core.isTrigger("blockburn");
        onblockform = core.isTrigger("blockformed");
        onblockfade = core.isTrigger("blockfaded");
        onblockspread = core.isTrigger("blockspread");
        onblockfromto = core.isTrigger("blockfromto");
        onblockphysics = core.isTrigger("blockphysics");
        onpiston = core.isTrigger("pistonmoved");
        onblockredstone = core.isTrigger("blockredstone");
        
        if(onplace) {
            Listener placelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPlace(BlockPlaceEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());                  	
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
                }
            };
            pm.registerEvents(placelistener, this);
        }
        
        if(onbreak) {
            Listener breaklistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockBreak(BlockBreakEvent event) {
                    Block b = event.getBlock();
                    if(b == null) return;   /* Stupid mod workaround */
                    Location loc = b.getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                	invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());               	
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
                }
            };
            pm.registerEvents(breaklistener, this);
        }
        
        if(onleaves) {
            Listener leaveslistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onLeavesDecay(LeavesDecayEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());         	
                    if(onleaves) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "leavesdecay");
                    }
                }
            };
            pm.registerEvents(leaveslistener, this);
        }

        if(onburn) {
            Listener burnlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockBurn(BlockBurnEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                	invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());      	
                    if(onburn) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockburn");
                    }
                }
            };
            pm.registerEvents(burnlistener, this);
        }
        
        if(onblockphysics) {
            Listener physlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPhysics(BlockPhysicsEvent event) {
                    Block b = event.getBlock();
                    Material m = b.getType();
                    if(m == null) return;
                    switch(m) {
                        //case STATIONARY_WATER:
                        case WATER:
                        //case STATIONARY_LAVA:
                        case LAVA:
                        case GRAVEL:
                        case SAND:
                            checkBlock(b, "blockphysics");
                            break;
                        default:
                            break;
                    }
                }
            };
            pm.registerEvents(physlistener, this);
        }
        
        if(onblockfromto) {
            Listener fromtolistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockFromTo(BlockFromToEvent event) {
                    Block b = event.getBlock();
                    Material m = b.getType();
                    if(!Tag.PRESSURE_PLATES.isTagged(m))
                        checkBlock(b, "blockfromto");
                    b = event.getToBlock();
                    m = b.getType();
                    if(!Tag.PRESSURE_PLATES.isTagged(m))
                        checkBlock(b, "blockfromto");
                }
            };
            pm.registerEvents(fromtolistener, this);
        }
        
        if(onpiston) {
            Listener pistonlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPistonRetract(BlockPistonRetractEvent event) {
                    Block b = event.getBlock();
                    Location loc = b.getLocation();
                    BlockFace dir = event.getDirection();
                    String wn = getWorld(loc.getWorld()).getName();
                    int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                    invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 2; i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }

                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPistonExtend(BlockPistonExtendEvent event) {
                    Block b = event.getBlock();
                    Location loc = b.getLocation();
                    BlockFace dir = event.getDirection();
                    String wn = getWorld(loc.getWorld()).getName();
                    int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                    invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 1+event.getLength(); i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }
            };
            pm.registerEvents(pistonlistener, this);
        }
        
        if(onblockspread) {
            Listener spreadlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockSpread(BlockSpreadEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockspread");
                }
            };
            pm.registerEvents(spreadlistener, this);
        }
        
        if(onblockform) {
            Listener formlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockForm(BlockFormEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockform");
                }
            };
            pm.registerEvents(formlistener, this);
        }
        
        if(onblockfade) {
            Listener fadelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockFade(BlockFadeEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfade");
                }
            };
            pm.registerEvents(fadelistener, this);
        }
        
        onblockgrow = core.isTrigger("blockgrow");
        
        if(onblockgrow) {
            Listener growTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockGrow(BlockGrowEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockgrow");
                }
            };
            pm.registerEvents(growTrigger, this);
        }
        
        if(onblockredstone) {
            Listener redstoneTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockRedstone(BlockRedstoneEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockredstone");
                }
            };
            pm.registerEvents(redstoneTrigger,  this);
        }
        
        /* Register player event trigger handlers */
        Listener playerTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onPlayerJoin(PlayerJoinEvent event) {
                if(onplayerjoin) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(getWorld(loc.getWorld()).getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
                }
            }
        };

        onplayerjoin = core.isTrigger("playerjoin");
        onplayermove = core.isTrigger("playermove");
        pm.registerEvents(playerTrigger, this);
        
        if(onplayermove) {
            Listener playermove = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onPlayerMove(PlayerMoveEvent event) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(getWorld(loc.getWorld()).getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playermove");
                }
            };
            pm.registerEvents(playermove, this);
            Log.warning("playermove trigger enabled - this trigger can cause excessive tile updating: use with caution");
        }
        /* Register entity event triggers */
        Listener entityTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onEntityExplode(EntityExplodeEvent event) {
                Location loc = event.getLocation();
                String wname = getWorld(loc.getWorld()).getName();
                int minx, maxx, miny, maxy, minz, maxz;
                minx = maxx = loc.getBlockX();
                miny = maxy = loc.getBlockY();
                minz = maxz = loc.getBlockZ();
                /* Calculate volume impacted by explosion */
                List<Block> blocks = event.blockList();
                for(Block b: blocks) {
                    Location l = b.getLocation();
                    int x = l.getBlockX();
                    if(x < minx) minx = x;
                    if(x > maxx) maxx = x;
                    int y = l.getBlockY();
                    if(y < miny) miny = y;
                    if(y > maxy) maxy = y;
                    int z = l.getBlockZ();
                    if(z < minz) minz = z;
                    if(z > maxz) maxz = z;
                }
                invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onexplosion) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "entityexplode");
                }
            }
        };
        onexplosion = core.isTrigger("explosion");
        pm.registerEvents(entityTrigger, this);
        
        /* Register world event triggers */
        Listener worldTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onWorldLoad(WorldLoadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onWorldUnload(WorldUnloadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(w != null) {
                    core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
                    w.setWorldUnloaded();
                    core.processWorldUnload(w);
                }
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onStructureGrow(StructureGrowEvent event) {
                Location loc = event.getLocation();
                String wname = getWorld(loc.getWorld()).getName();
                int minx, maxx, miny, maxy, minz, maxz;
                minx = maxx = loc.getBlockX();
                miny = maxy = loc.getBlockY();
                minz = maxz = loc.getBlockZ();
                /* Calculate volume impacted by explosion */
                List<BlockState> blocks = event.getBlocks();
                for(BlockState b: blocks) {
                    int x = b.getX();
                    if(x < minx) minx = x;
                    if(x > maxx) maxx = x;
                    int y = b.getY();
                    if(y < miny) miny = y;
                    if(y > maxy) maxy = y;
                    int z = b.getZ();
                    if(z < minz) minz = z;
                    if(z > maxz) maxz = z;
                }
                invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onstructuregrow) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "structuregrow");
                }
            }
        };
        onstructuregrow = core.isTrigger("structuregrow");
        // To link configuration to real loaded worlds.
        pm.registerEvents(worldTrigger, this);

        ongeneratechunk = core.isTrigger("chunkgenerated");
        if(ongeneratechunk) {
            Listener chunkTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onChunkPopulate(ChunkPopulateEvent event) {
                	DynmapWorld dw = getWorld(event.getWorld());
                    Chunk c = event.getChunk();
                    /* Touch extreme corners */
                    int x = c.getX() << 4;
                    int z = c.getZ() << 4;
                    int ymin = dw.minY;
                    int ymax = dw.worldheight;
                    mapManager.touchVolume(getWorld(event.getWorld()).getName(), x, ymin, z, x+15, ymax, z+16, "chunkpopulate");
                }
            };
            pm.registerEvents(chunkTrigger, this);
        }
    }

    @Override
    public void assertPlayerInvisibility(String player, boolean is_invisible,
            String plugin_id) {
        core.assertPlayerInvisibility(player, is_invisible, plugin_id);
    }

    @Override
    public void assertPlayerInvisibility(Player player, boolean is_invisible,
            Plugin plugin) {
        core.assertPlayerInvisibility(player.getName(), is_invisible, plugin.getName());
    }

    @Override
    public void assertPlayerVisibility(String player, boolean is_visible,
            String plugin_id) {
        core.assertPlayerVisibility(player, is_visible, plugin_id);
    }

    @Override
    public void assertPlayerVisibility(Player player, boolean is_visible,
            Plugin plugin) {
        core.assertPlayerVisibility(player.getName(), is_visible, plugin.getName());
    }
    @Override
    public boolean setDisableChatToWebProcessing(boolean disable) {
        return core.setDisableChatToWebProcessing(disable);
    }
    @Override
    public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see) {
        return core.testIfPlayerVisibleToPlayer(player, player_to_see);
    }
    @Override
    public boolean testIfPlayerInfoProtected() {
        return false; //Removed
    }

    @Override
    public void processSignChange(String material, String world, int x, int y, int z,
            String[] lines, String playerid) {
        core.processSignChange(material, world, x, y, z, lines, playerid);
    }
    
    Polygon getWorldBorder(World w) {
        return helper.getWorldBorder(w);
    }
}
