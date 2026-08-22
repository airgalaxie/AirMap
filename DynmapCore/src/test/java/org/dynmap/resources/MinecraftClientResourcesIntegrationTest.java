package org.dynmap.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.dynmap.DynmapCore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MinecraftClientResourcesIntegrationTest {
    @Test
    void resolvesStoneFromBlockstateThroughParentsToRealPng() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        String version = MinecraftClientResources.configuredVersion();
        DynmapCore core = new DynmapCore();
        core.setDataFolder(Path.of(cache).toFile());
        core.setMinecraftVersion(version);
        MinecraftResourceProvider resources = core.getMinecraftResourceProvider();

        JsonObject blockstate = json(resources, "minecraft:blockstates/stone.json");
        String modelId = blockstate.getAsJsonObject("variants").getAsJsonArray("").get(0)
                .getAsJsonObject().get("model").getAsString();
        Map<String, String> textures = new HashMap<>();
        JsonObject model = resolve(resources, modelId, textures);
        assertNotNull(model.getAsJsonArray("elements"), "resolved parent supplies cube elements");
        String textureId = textures.get("all");
        assertEquals("minecraft:block/stone", textureId);

        try (var png = resources.open(toResource(textureId, "textures/", ".png"))) {
            BufferedImage image = ImageIO.read(png);
            assertNotNull(image, "texture contains decodable PNG data");
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        }
    }

    private static JsonObject resolve(MinecraftResourceProvider resources, String id, Map<String, String> textures) throws Exception {
        JsonObject own = json(resources, toResource(id, "models/", ".json"));
        JsonObject resolved = new JsonObject();
        if (own.has("parent") && !own.get("parent").getAsString().startsWith("builtin/")) {
            resolved = resolve(resources, qualify(own.get("parent").getAsString(), namespace(id)), textures);
        }
        if (own.has("textures")) own.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        for (var entry : own.entrySet()) resolved.add(entry.getKey(), entry.getValue().deepCopy());
        return resolved;
    }

    private static JsonObject json(MinecraftResourceProvider resources, String id) throws Exception {
        try (var input = resources.open(id); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String toResource(String id, String prefix, String suffix) {
        String qualified = qualify(id, "minecraft");
        return namespace(qualified) + ":" + prefix + qualified.substring(qualified.indexOf(':') + 1) + suffix;
    }
    private static String qualify(String id, String namespace) { return id.contains(":") ? id : namespace + ":" + id; }
    private static String namespace(String id) { return id.substring(0, id.indexOf(':')); }
}
