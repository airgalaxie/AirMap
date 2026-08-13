package org.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dynmap.Event.Listener;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        core.events.<JsonObject>addListener("buildclientconfiguration", new Listener<JsonObject>() {
            @Override
            public void triggered(JsonObject root) {
                ConfigurationNode c = core.configuration;
                root.addProperty("confighash", core.getConfigHashcode());
                root.addProperty("updaterate", c.getFloat("updaterate", 1.0f));
                root.addProperty("showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
                root.addProperty("joinmessage", c.getString("joinmessage", "%playername% joined"));
                root.addProperty("quitmessage", c.getString("quitmessage", "%playername% quit"));
                root.addProperty("spammessage", c.getString("spammessage", ""));
                root.addProperty("webprefix", unescapeString(c.getString("webprefix", "[WEB] ")));
                root.addProperty("defaultzoom", c.getInteger("defaultzoom", 0));
                root.addProperty("sidebaropened", c.getString("sidebaropened", "false"));
                root.addProperty("dynmapversion", core.getDynmapPluginVersion());
                root.addProperty("coreversion", core.getDynmapCoreVersion());
                root.addProperty("cyrillic", c.getBoolean("cyrillic-support", false));
                root.addProperty("showlayercontrol", c.getString("showlayercontrol", "true"));
                root.addProperty("grayplayerswhenhidden", c.getBoolean("grayplayerswhenhidden", true));
                root.addProperty("login-enabled", false);
                String sn = core.getServer().getServerName();
                if(sn.equals("Unknown Server"))
                    sn = "Minecraft Dynamic Map";
                root.addProperty("title", c.getString("webpage-title", sn));
                root.addProperty("msg-maptypes", c.getString("msg/maptypes", "Map Types"));
                root.addProperty("msg-players", c.getString("msg/players", "Players"));
                root.addProperty("msg-chatrequireslogin", "");
                root.addProperty("msg-chatnotallowed", "");
                root.addProperty("msg-hiddennamejoin", c.getString("msg/hiddennamejoin", "Player joined"));
                root.addProperty("msg-hiddennamequit", c.getString("msg/hiddennamequit", "Player quit"));
                root.addProperty("maxcount", core.getMaxPlayers());
                
                DynmapWorld defaultWorld = null;
                String defmap = null;
                JsonArray worlds = new JsonArray();
                root.add("worlds", worlds);
                for(DynmapWorld world : core.mapManager.getWorlds()) {
                    if (world.maps.size() == 0) continue;
                    if (defaultWorld == null) defaultWorld = world;
                    JsonObject worldObject = new JsonObject();
                    worldObject.addProperty("name", world.getName());
                    worldObject.addProperty("storageid", world.getStorageId());
                    worldObject.addProperty("tileid", world.getMapStorage().getTileWorldId(world));
                    worldObject.addProperty("title", world.getTitle());
                    worldObject.addProperty("protected", false);
                    DynmapLocation center = world.getCenterLocation();
                    JsonObject centerObject = new JsonObject();
                    centerObject.addProperty("x", center.x);
                    centerObject.addProperty("y", center.y);
                    centerObject.addProperty("z", center.z);
                    worldObject.add("center", centerObject);
                    worldObject.addProperty("extrazoomout", world.getExtraZoomOutLevels());
                    worldObject.addProperty("sealevel", world.sealevel);
                    worldObject.addProperty("worldheight", world.worldheight);
                    worlds.add(worldObject);
                    
                    for(MapType mt : world.maps) {
                        mt.buildClientConfiguration(worldObject, world);
                        if(defmap == null) defmap = mt.getName();
                    }
                }
                root.addProperty("defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.getName()));
                root.addProperty("defaultmap", c.getString("defaultmap", defmap == null ? "surface" : defmap));
                if(c.getString("followmap", null) != null)
                    root.addProperty("followmap", c.getString("followmap"));
                if(c.getInteger("followzoom",-1) >= 0)
                    root.addProperty("followzoom", c.getInteger("followzoom", 0));
            }
        });
    }
    
}
