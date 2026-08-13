package org.dynmap;

import com.google.gson.JsonObject;

public class ClientUpdateEvent {
    public long timestamp;
    public DynmapWorld world;
    public JsonObject update;
    public String user;
    public boolean include_all_users;
    
    public ClientUpdateEvent(long timestamp, DynmapWorld world, JsonObject update) {
        this.timestamp = timestamp;
        this.world = world;
        this.update = update;
    }
}
