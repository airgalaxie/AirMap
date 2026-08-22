package org.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.web.Json;

public class ClientUpdateComponent extends Component {
    private int hideifshadow;
    private int hideifunder;
    private boolean hideifsneaking;
    private boolean hideifspectator;
    private boolean hideifinvisiblepotion;
    private boolean hideIfVanished;
    public static boolean usePlayerColors;
    public static boolean hideNames;
    
    public ClientUpdateComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        
        hideNames = configuration.getBoolean("hidenames", false);
        hideifshadow = configuration.getInteger("hideifshadow", 15);
        hideifunder = configuration.getInteger("hideifundercover", 15);
        hideifsneaking = configuration.getBoolean("hideifsneaking", false);
        hideifspectator = configuration.getBoolean("hideifspectator", false);
        hideifinvisiblepotion = configuration.getBoolean("hide-if-invisiblity-potion", true);
        hideIfVanished = configuration.getBoolean("hide-if-vanished", true);
        usePlayerColors = configuration.getBoolean("use-name-colors", false);
        
        core.events.addListener("buildclientupdate", new Event.Listener<ClientUpdateEvent>() {
            @Override
            public void triggered(ClientUpdateEvent e) {
                buildClientUpdate(e);
            }
        });
    }
    
    protected void buildClientUpdate(ClientUpdateEvent e) {
        DynmapWorld world = e.world;
        JsonObject update = e.update;
        long since = e.timestamp;
        String worldName = world.getName();
        
        update.addProperty("confighash", core.getConfigHashcode());
        update.addProperty("servertime", world.getTime() % 24000);
        update.addProperty("hasStorm", world.hasStorm());
        update.addProperty("isThundering", world.isThundering());

        JsonArray playersJson = new JsonArray();
        update.add("players", playersJson);
        List<DynmapPlayer> players = core.playerList.getVisiblePlayers();
        for(DynmapPlayer p : players) {
            boolean hide = false;
            DynmapLocation pl = p.getLocation();
            DynmapWorld pw = core.getWorld(pl.world);
            if(pw == null) {
                hide = true;
            }
            JsonObject playerJson = new JsonObject();
            
            playerJson.addProperty("type", "player");
            if (hideNames)
                playerJson.addProperty("name", "");
            else if (usePlayerColors)
                playerJson.addProperty("name", Client.encodeColorInHTML(p.getDisplayName()));
            else
                playerJson.addProperty("name", Client.stripColor(p.getDisplayName()));
            playerJson.addProperty("account", p.getName());
            if((!hide) && (hideifshadow < 15)) {
                if(pw.getLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifshadow) {
                    hide = true;
                }
            }
            if((!hide) && (hideifunder < 15)) {
                if(pw.canGetSkyLightLevel()) { /* If we can get real sky level */
                    if(pw.getSkyLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifunder) {
                        hide = true;
                    }
                }
                else if(pw.isNether() == false) {   /* Not nether */
                    if(pw.getHighestBlockYAt((int)pl.x, (int)pl.z) > pl.y) {
                        hide = true;
                    }
                }
            }
            if((!hide) && hideifsneaking && p.isSneaking()) {
                hide = true;
            }
            if((!hide) && hideifspectator && p.isSpectator()) {
                hide = true;
            }
            if((!hide) && hideifinvisiblepotion && p.isInvisible()) {
                hide = true;
            }
            if(hideIfVanished && p.isVanished()) {
                continue;
            }
                
            /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
            DynmapWorld pworld = MapManager.mapman.worldsLookup.get(pl.world);
            /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
            if(configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true) &&
                    (pworld != null) && pworld.sendposition && (!hide)) {
                playerJson.addProperty("world", pl.world);
                playerJson.addProperty("x", pl.x);
                playerJson.addProperty("y", pl.y);
                playerJson.addProperty("z", pl.z);
            }
            else {
                playerJson.addProperty("world", "-some-other-bogus-world-");
                playerJson.addProperty("x", 0.0);
                playerJson.addProperty("y", 64.0);
                playerJson.addProperty("z", 0.0);
            }
            /* Only send health if enabled AND we're on visible world */
            if (configuration.getBoolean("sendhealth", false) && (pworld != null) && pworld.sendhealth && (!hide)) {
                playerJson.addProperty("health", p.getHealth());
                playerJson.addProperty("armor", p.getArmorPoints());
            }
            else {
                playerJson.addProperty("health", 0);
                playerJson.addProperty("armor", 0);
            }
            playerJson.addProperty("sort", p.getSortWeight());
            playersJson.add(playerJson);
        }
        List<DynmapPlayer> hidden = core.playerList.getHiddenPlayers();
        if(configuration.getBoolean("includehiddenplayers", false)) {
            for(DynmapPlayer p : hidden) {
                JsonObject playerJson = new JsonObject();
                playerJson.addProperty("type", "player");
                if (hideNames) 
                    playerJson.addProperty("name", "");
                else if (usePlayerColors)
                    playerJson.addProperty("name", Client.encodeColorInHTML(p.getDisplayName()));
                else
                    playerJson.addProperty("name", Client.stripColor(p.getDisplayName()));
                playerJson.addProperty("account", p.getName());
                playerJson.addProperty("world", "-hidden-player-");
                playerJson.addProperty("x", 0.0);
                playerJson.addProperty("y", 64.0);
                playerJson.addProperty("z", 0.0);
                playerJson.addProperty("health", 0);
                playerJson.addProperty("armor", 0);
                playerJson.addProperty("sort", p.getSortWeight());
                playersJson.add(playerJson);
            }
            update.addProperty("currentcount", core.getCurrentPlayers());
        }
        else {
            update.addProperty("currentcount", core.getCurrentPlayers() - hidden.size());
        }

        JsonArray updates = new JsonArray();
        update.add("updates", updates);
        for(Object worldUpdate : core.mapManager.getWorldUpdates(worldName, since)) {
            updates.add(Json.toJsonTree((Client.Update) worldUpdate));
        }
    }

}
