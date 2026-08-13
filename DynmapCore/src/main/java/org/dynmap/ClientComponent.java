package org.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dynmap.web.Json;
public class ClientComponent extends Component {
    private boolean disabled;
    
    public ClientComponent(final DynmapCore plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JsonObject>() {
            @Override
            public void triggered(JsonObject root) {
                if(!disabled)
                    buildClientConfiguration(root);
            }
        });
    }
    
    protected void disableComponent() {
        disabled = true;
    }
    
    protected void buildClientConfiguration(JsonObject root) {
        JsonObject component = createClientConfiguration();
        JsonArray components = root.getAsJsonArray("components");
        if (components == null) {
            components = new JsonArray();
            root.add("components", components);
        }
        components.add(component);
    }
    
    protected JsonObject createClientConfiguration() {
        JsonObject component = Json.toJsonTree(configuration).getAsJsonObject();
        component.remove("class");
        return component;
    }

}
