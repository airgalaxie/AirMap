package org.dynmap.hdmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Resolves Minecraft special/block-entity models to immutable, static model layers. */
final class MinecraftModelLayerRegistry {
    private static final String RESOURCE = "/minecraft-model-layers/registry.json";

    private final JsonArray rules;

    private MinecraftModelLayerRegistry(JsonArray rules) {
        this.rules = rules;
    }

    static MinecraftModelLayerRegistry load() throws IOException {
        try (InputStream in = MinecraftModelLayerRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("Missing Minecraft model-layer registry " + RESOURCE);
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new MinecraftModelLayerRegistry(root.getAsJsonArray("rules"));
        }
    }

    Binding resolve(String specialType, String modelId, Map<String, String> state) {
        for (JsonElement value : rules) {
            JsonObject rule = value.getAsJsonObject();
            if (!matches(rule.getAsJsonObject("match"), specialType, modelId)) continue;
            String layer = selectLayer(rule.get("layer"), modelId, state);
            if (layer == null) return null;
            String textureDirectory = rule.has("texture_directory")
                    ? rule.get("texture_directory").getAsString() : "";
            return new Binding(layer, textureDirectory);
        }
        return null;
    }

    record Binding(String layer, String textureDirectory) {}

    private static boolean matches(JsonObject match, String specialType, String modelId) {
        if (match.has("special_type")
                && !match.get("special_type").getAsString().equals(specialType)) return false;
        if (match.has("model") && !match.get("model").getAsString().equals(modelId)) return false;
        if (match.has("model_prefix")
                && (modelId == null || !modelId.startsWith(match.get("model_prefix").getAsString()))) return false;
        return true;
    }

    private static String selectLayer(JsonElement definition, String modelId, Map<String, String> state) {
        if (definition.isJsonPrimitive()) return definition.getAsString();
        JsonObject selection = definition.getAsJsonObject();
        if (selection.has("model_basename") && selection.get("model_basename").getAsBoolean()) {
            if (modelId == null) return null;
            int slash = modelId.lastIndexOf('/');
            return slash < 0 ? modelId : modelId.substring(slash + 1);
        }
        String selected = state.get(selection.get("property").getAsString());
        JsonObject cases = selection.getAsJsonObject("cases");
        if (selected != null && cases.has(selected)) return cases.get(selected).getAsString();
        return selection.has("fallback") ? selection.get("fallback").getAsString() : null;
    }
}
