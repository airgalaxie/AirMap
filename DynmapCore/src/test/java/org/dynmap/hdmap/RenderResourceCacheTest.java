package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.LayeredMinecraftResourceProvider;
import org.dynmap.resources.MinecraftResourceProvider;
import org.dynmap.resources.MinecraftClientResources;
import org.dynmap.resources.ZipMinecraftResourceProvider;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderResourceCacheTest {
    @TempDir Path directory;

    @Test
    void warmLoadPreservesGeometryUvShadingWeightsTexturesAndFreshFluidRenderer() throws Exception {
        Fixture fixture = fixture();
        DynmapBlockState water = DynmapBlockState.getBaseStateByName("minecraft:water");
        if (water == DynmapBlockState.AIR) water = new DynmapBlockState.Builder().setBlockName("minecraft:water").build();
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        Path file = RenderResourceCache.cacheFile(directory, "render-cache");
        assertTrue(Files.isRegularFile(file));
        byte[] saved = Files.readAllBytes(file);
        var before = model(fixture.state);
        var fluid = HDBlockModels.models_by_id_data[water.globalStateIndex];
        int[] faces = HDBlockStateTextureMap.getByBlockState(fixture.state).faces.clone();
        var transparency = HDBlockStateTextureMap.getByBlockState(fixture.state).trans;
        int maxPatches = HDBlockModels.getMaxPatchCount();
        int opens = fixture.resources.bakingOpens;

        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());

        assertEquals(opens, fixture.resources.bakingOpens, "cache hit must not parse JSON or inspect PNGs again");
        assertArrayEquals(saved, Files.readAllBytes(file), "a hit must not rewrite the snapshot");
        for (int x = -16; x <= 16; x++) {
            assertArrayEquals(before.getPatches(x, 64, -x), model(fixture.state).getPatches(x, 64, -x),
                    "weighted multipart geometry/UVs/shading must survive at every tested position");
        }
        assertEquals(maxPatches, HDBlockModels.getMaxPatchCount());
        assertEquals(transparency, HDBlockStateTextureMap.getByBlockState(fixture.state).trans);
        assertArrayEquals(faces, HDBlockStateTextureMap.getByBlockState(fixture.state).faces);
        assertInstanceOf(CustomBlockModel.class, HDBlockModels.models_by_id_data[water.globalStateIndex]);
        assertNotSame(fluid, HDBlockModels.models_by_id_data[water.globalStateIndex]);
    }

    @Test
    void changedModelContentsAndRegistryPropertiesRebuildTheCache() throws Exception {
        Fixture fixture = fixture();
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        int opens = fixture.resources.bakingOpens;
        fixture.resources.assets.put("test:models/block/cached.json", modelJson(12).getBytes(StandardCharsets.UTF_8));
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertTrue(fixture.resources.bakingOpens > opens);
        assertTrue(java.util.Arrays.stream(model(fixture.state).getPatches()).anyMatch(p -> p.y0 == 0.75));
        String original = RenderResourceCache.fingerprint(fixture.resources);
        fixture.state.lightAttenuation = 14;
        assertNotEquals(original, RenderResourceCache.fingerprint(fixture.resources));
        opens = fixture.resources.bakingOpens;
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertTrue(fixture.resources.bakingOpens > opens);
    }

    @Test
    void corruptCacheFallsBackAndReplacesBrokenFile() throws Exception {
        Fixture fixture = fixture();
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        Path file = RenderResourceCache.cacheFile(directory, "render-cache");
        byte[] damaged = Files.readAllBytes(file);
        damaged[damaged.length - 8] ^= 1; // gzip CRC must be checked before publication
        Files.write(file, damaged);
        assertThrows(IOException.class, () -> RenderResourceCache.read(file,
                RenderResourceCache.fingerprint(fixture.resources), new PatchDefinitionFactory()));
        int opens = fixture.resources.bakingOpens;
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertTrue(fixture.resources.bakingOpens > opens);
        assertNotNull(RenderResourceCache.read(file, RenderResourceCache.fingerprint(fixture.resources), new PatchDefinitionFactory()));
        assertNotNull(model(fixture.state));
    }

    @Test
    void disabledCacheDoesNotReadWriteOrFingerprint() throws Exception {
        Fixture fixture = fixture();
        var configuration = new ConfigurationNode(Map.of("render-cache", Map.of("enabled", false)));
        HDBlockModels.loadModels(fixture.core, configuration);
        HDBlockModels.loadModels(fixture.core, configuration);
        assertEquals(0, fixture.resources.fingerprints);
        assertFalse(Files.exists(directory.resolve("render-cache")));
        assertNotNull(model(fixture.state));
    }

    @Test
    void customDirectoryWorksAndUnwritableCacheDoesNotPreventModelLoading() throws Exception {
        Fixture fixture = fixture();
        var configuration = new ConfigurationNode(Map.of("render-cache", Map.of("directory", "custom/models")));
        HDBlockModels.loadModels(fixture.core, configuration);
        assertTrue(Files.isRegularFile(directory.resolve("custom/models/models.bin.gz")));
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var invalid = new ConfigurationNode(Map.of("render-cache", Map.of("directory", blocked.toString())));
        assertDoesNotThrow(() -> HDBlockModels.loadModels(fixture.core, invalid));
        assertNotNull(model(fixture.state));
        assertEquals(directory.resolve("absolute/models.bin.gz"), RenderResourceCache.cacheFile(directory,
                directory.resolve("absolute").toString()));
    }

    @Test
    void failedFingerprintOrIncompleteModelLoadDoesNotPublishCache() throws Exception {
        Fixture fixture = fixture();
        fixture.resources.failFingerprint = true;
        assertDoesNotThrow(() -> HDBlockModels.loadModels(fixture.core, new ConfigurationNode()));
        assertNotNull(model(fixture.state));
        assertFalse(Files.exists(directory.resolve("render-cache/models.bin.gz")));
        fixture.resources.failFingerprint = false;
        fixture.resources.assets.put("test:models/block/cached.json", "invalid".getBytes(StandardCharsets.UTF_8));
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertFalse(Files.exists(directory.resolve("render-cache/models.bin.gz")));
    }

    @Test
    void fingerprintsDetectSameLengthContentChangesAddedRemovedAssetsAndLayerOrder() throws Exception {
        var lower = new MemoryResources();
        lower.assets.put("test:models/a.json", new byte[] {1, 2});
        var upper = new MemoryResources();
        upper.assets.put("test:models/a.json", new byte[] {3, 4});
        String original = lower.fingerprint();
        lower.assets.put("test:models/a.json", new byte[] {2, 1});
        assertNotEquals(original, lower.fingerprint());
        String changed = lower.fingerprint();
        lower.assets.put("test:items/new.json", new byte[0]);
        assertNotEquals(changed, lower.fingerprint());
        lower.assets.remove("test:items/new.json");
        assertEquals(changed, lower.fingerprint());
        assertNotEquals(new LayeredMinecraftResourceProvider(lower, upper).fingerprint(),
                new LayeredMinecraftResourceProvider(upper, lower).fingerprint());
        Path zip = directory.resolve("pack.zip");
        writeZip(zip, new byte[] {1, 2});
        var modified = Files.getLastModifiedTime(zip);
        var source = new ZipMinecraftResourceProvider(zip);
        String archiveFingerprint = source.fingerprint();
        writeZip(zip, new byte[] {2, 1});
        Files.setLastModifiedTime(zip, modified);
        assertNotEquals(archiveFingerprint, source.fingerprint());
    }

    @Test
    void mismatchedIdentityRejectsSnapshot() throws Exception {
        Fixture fixture = fixture();
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertNull(RenderResourceCache.read(RenderResourceCache.cacheFile(directory, "render-cache"),
                "another build or resource set", new PatchDefinitionFactory()));
    }

    @Test
    void failedTextureRestoreResetsPartialRegistrationsBeforeFallback() throws Exception {
        Fixture fixture = fixture();
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        int[] faces = HDBlockStateTextureMap.getByBlockState(fixture.state).faces.clone();
        String identity = RenderResourceCache.fingerprint(fixture.resources);
        Path file = RenderResourceCache.cacheFile(directory, "render-cache");
        var saved = RenderResourceCache.read(file, identity, new PatchDefinitionFactory());
        var invalidTextures = new LinkedHashMap<>(saved.textures());
        invalidTextures.replaceAll((name, tile) -> tile + 1);
        RenderResourceCache.write(file, identity,
                new RenderResourceCache.Snapshot(invalidTextures, saved.patches(), saved.states()));
        int opens = fixture.resources.bakingOpens;
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertTrue(fixture.resources.bakingOpens > opens);
        assertArrayEquals(faces, HDBlockStateTextureMap.getByBlockState(fixture.state).faces);
    }

    @Test
    void regularAndAtlasTextureAliasesSurviveTogether() throws Exception {
        Fixture fixture = fixture();
        String chestName = "chest_" + System.nanoTime();
        var chest = new DynmapBlockState.Builder().setBlockName("test:" + chestName)
                .setStateName("facing=north,type=single,waterlogged=false").build();
        var assets = fixture.resources.assets;
        assets.put("test:blockstates/" + chestName + ".json", """
                {"variants":{"":{"model":"test:block/chest"}}}
                """.getBytes(StandardCharsets.UTF_8));
        assets.put("test:models/block/chest.json", "{}".getBytes(StandardCharsets.UTF_8));
        assets.put("test:items/" + chestName + ".json", """
                {"model":{"type":"minecraft:special","model":{"type":"minecraft:chest","texture":"test:block/cached"}}}
                """.getBytes(StandardCharsets.UTF_8));
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertEquals(18, model(chest).getPatches().length);
        PatchDefinition[] before = model(chest).getPatches();
        Path file = RenderResourceCache.cacheFile(directory, "render-cache");
        var saved = RenderResourceCache.read(file, RenderResourceCache.fingerprint(fixture.resources), new PatchDefinitionFactory());
        assertEquals(saved.textures().get("test:block/cached"), saved.textures().get("atlas\u0000test:block/cached"));
        int opens = fixture.resources.bakingOpens;
        HDBlockModels.loadModels(fixture.core, new ConfigurationNode());
        assertEquals(opens, fixture.resources.bakingOpens);
        assertArrayEquals(before, model(chest).getPatches());
        var addonField = TexturePack.class.getDeclaredField("addonfiles");
        addonField.setAccessible(true);
        boolean atlasPreserved = false;
        for (Object addon : (java.util.List<?>) addonField.get(null)) {
            var flag = addon.getClass().getDeclaredField("preserveResolution");
            flag.setAccessible(true);
            atlasPreserved |= flag.getBoolean(addon);
        }
        assertTrue(atlasPreserved, "atlas registrations must preserve their original pixel resolution");
    }

    @Test
    void roundTripsRepresentativeModelsFromConfiguredMinecraftClient() throws Exception {
        String cache = System.getenv("AIRMAP_MINECRAFT_TEST_CACHE");
        org.junit.jupiter.api.Assumptions.assumeTrue(cache != null && !cache.isBlank(), "actual-client test cache was not requested");
        var resources = MinecraftClientResources.provision(Path.of(cache).resolve("minecraft-resources"),
                MinecraftClientResources.configuredVersion(), false);
        var states = new java.util.ArrayList<DynmapBlockState>();
        for (String[] description : new String[][] {
                {"minecraft:stone", ""},
                {"minecraft:oak_stairs", "facing=east,half=bottom,shape=straight,waterlogged=false"},
                {"minecraft:oak_fence", "east=true,north=false,south=true,waterlogged=false,west=false"},
                {"minecraft:chest", "facing=north,type=single,waterlogged=false"},
                {"minecraft:shulker_box", "facing=up"},
                {"minecraft:bell", "attachment=floor,facing=east,powered=false"}}) {
            states.add(new DynmapBlockState.Builder().setBlockName(description[0]).setStateName(description[1]).build());
        }
        DynmapCore core = new DynmapCore() {
            @Override public MinecraftResourceProvider getMinecraftResourceProvider() { return resources; }
        };
        core.setDataFolder(directory.toFile());
        HDBlockModels.loadModels(core, new ConfigurationNode());
        var before = new java.util.ArrayList<PatchDefinition[]>();
        var faces = new java.util.ArrayList<int[]>();
        for (var state : states) {
            assertNotNull(model(state), state.blockName);
            before.add(model(state).getPatches(13, 64, -7));
            faces.add(HDBlockStateTextureMap.getByBlockState(state).faces.clone());
        }
        Path file = RenderResourceCache.cacheFile(directory, "render-cache");
        assertTrue(Files.isRegularFile(file), "actual-client model load must produce a complete snapshot");
        byte[] saved = Files.readAllBytes(file);
        HDBlockModels.loadModels(core, new ConfigurationNode());
        assertArrayEquals(saved, Files.readAllBytes(file));
        for (int i = 0; i < states.size(); i++) {
            assertArrayEquals(before.get(i), model(states.get(i)).getPatches(13, 64, -7), states.get(i).blockName);
            assertArrayEquals(faces.get(i), HDBlockStateTextureMap.getByBlockState(states.get(i)).faces, states.get(i).blockName);
        }
    }

    private Fixture fixture() throws Exception {
        String name = "cached_" + System.nanoTime();
        DynmapBlockState state = new DynmapBlockState.Builder().setBlockName("test:" + name).setAttenuatesLight(15).build();
        var resources = new MemoryResources();
        resources.assets.put("test:blockstates/" + name + ".json", ("""
                {"multipart":[
                  {"apply":[{"model":"test:block/cached","weight":1},{"model":"test:block/cached","y":90,"weight":3}]},
                  {"apply":{"model":"test:block/cached","y":180}}
                ]}
                """).getBytes(StandardCharsets.UTF_8));
        resources.assets.put("test:models/block/cached.json", modelJson(16).getBytes(StandardCharsets.UTF_8));
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, 0xffffffff);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        resources.assets.put("test:textures/block/cached.png", png.toByteArray());
        DynmapCore core = new DynmapCore() {
            @Override public MinecraftResourceProvider getMinecraftResourceProvider() { return resources; }
        };
        core.setDataFolder(directory.toFile());
        return new Fixture(core, resources, state);
    }

    private static String modelJson(int height) {
        return """
                {"textures":{"all":"test:block/cached"},"elements":[
                  {"from":[0,0,0],"to":[16,%d,16],"shade_direction_override":"north","faces":{
                    "up":{"texture":"#all","uv":[2,3,14,15],"rotation":90,"tintindex":0},
                    "north":{"texture":"#all"}}}
                ]}
                """.formatted(height);
    }

    private static HDBlockPatchModel model(DynmapBlockState state) {
        return (HDBlockPatchModel) HDBlockModels.models_by_id_data[state.globalStateIndex];
    }

    private static void writeZip(Path path, byte[] contents) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            var entry = new ZipEntry("assets/test/models/a.json");
            entry.setTime(0);
            out.putNextEntry(entry);
            out.write(contents);
            out.closeEntry();
        }
    }

    private record Fixture(DynmapCore core, MemoryResources resources, DynmapBlockState state) { }

    private static final class MemoryResources implements MinecraftResourceProvider {
        final Map<String, byte[]> assets = new LinkedHashMap<>();
        int bakingOpens, fingerprints;
        boolean fingerprinting, failFingerprint;
        @Override public Set<String> list(String prefix, String suffix) {
            return assets.keySet().stream().filter(id -> id.substring(id.indexOf(':') + 1).startsWith(prefix)
                    && id.endsWith(suffix)).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        }
        @Override public InputStream open(String id) throws IOException {
            if (!fingerprinting) bakingOpens++;
            byte[] content = assets.get(id);
            if (content == null) throw new IOException("Missing " + id);
            return new ByteArrayInputStream(content);
        }
        @Override public String fingerprint() throws IOException {
            fingerprints++;
            if (failFingerprint) throw new IOException("Unavailable source");
            fingerprinting = true;
            try { return MinecraftResourceProvider.super.fingerprint(); }
            finally { fingerprinting = false; }
        }
    }
}
