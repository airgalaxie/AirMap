package org.dynmap.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftResourceProviderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void readsResourcePackPathsAndBinaryDataFromZip() throws Exception {
        Path pack = temporaryDirectory.resolve("client.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/blockstates/stone.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/minecraft/textures/block/stone.png"));
            zip.write(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
            zip.closeEntry();
        }

        MinecraftResourceProvider resources = new ZipMinecraftResourceProvider(pack);
        assertEquals(Set.of("minecraft:blockstates/stone.json"), resources.list("blockstates", ".json"));
        try (var input = resources.open("minecraft:textures/block/stone.png")) {
            assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, input.readAllBytes());
        }
    }

    @Test
    void higherLayerOverridesVanillaAndListsBothLayers() throws Exception {
        MinecraftResourceProvider vanilla = fixed("vanilla", "minecraft:models/block/stone.json");
        MinecraftResourceProvider overlay = fixed("overlay", "minecraft:models/block/stone.json", "mod:models/block/example.json");
        MinecraftResourceProvider resources = new LayeredMinecraftResourceProvider(overlay, vanilla);

        assertEquals("overlay", new String(resources.open("minecraft:models/block/stone.json").readAllBytes(), StandardCharsets.UTF_8));
        assertTrue(resources.list("models/block", ".json").containsAll(Set.of(
                "minecraft:models/block/stone.json", "mod:models/block/example.json")));
    }

    private static MinecraftResourceProvider fixed(String content, String... ids) {
        Set<String> available = Set.of(ids);
        return new MinecraftResourceProvider() {
            @Override public Set<String> list(String prefix, String suffix) {
                return available.stream().filter(id -> id.substring(id.indexOf(':') + 1).startsWith(prefix) && id.endsWith(suffix))
                        .collect(java.util.stream.Collectors.toSet());
            }
            @Override public java.io.InputStream open(String id) throws IOException {
                if (!available.contains(id)) throw new IOException("missing");
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
