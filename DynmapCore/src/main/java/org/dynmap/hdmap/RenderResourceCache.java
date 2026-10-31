package org.dynmap.hdmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.resources.MinecraftResourceProvider;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

/** Disposable, content-validated cache of baked Minecraft models and texture registrations. */
final class RenderResourceCache {
    private static final int MAGIC = 0x414d5243;
    private static final int FORMAT = 1;
    private static final int MAX_ENTRIES = 1_000_000;
    private static final String FILE_NAME = "models.bin.gz";

    private RenderResourceCache() { }

    static void loadModels(DynmapCore core, ConfigurationNode configuration) throws IOException {
        MinecraftResourceProvider resources = core.getMinecraftResourceProvider();
        Path file = null;
        String identity = null;
        if (configuration.getBoolean("render-cache/enabled", true)) {
            try {
                file = cacheFile(core.getDataFolder().toPath(),
                        configuration.getString("render-cache/directory", "render-cache"));
                identity = fingerprint(resources);
                if (Files.isRegularFile(file)) {
                    Snapshot saved = read(file, identity, HDBlockModels.pdf);
                    if (saved != null) {
                        saved.install(resources);
                        Log.info("Loaded Minecraft render resource cache: " + file);
                        return;
                    }
                    Log.info("Minecraft render resource cache changed; rebuilding");
                } else {
                    Log.info("Minecraft render resource cache missing; building");
                }
            } catch (IOException | RuntimeException e) {
                Log.warning("Cannot use Minecraft render resource cache; rebuilding: " + e.getMessage());
                // An installation failure must never leave partially restored registrations behind.
                HDBlockModels.resetModels();
            }
        }

        var loader = new MinecraftModelLoader(resources, HDBlockModels.pdf);
        loader.load();
        if (file != null && identity != null && loader.isComplete()) {
            try {
                // Do not publish data if a resource pack or the running development build changed mid-load.
                if (!identity.equals(fingerprint(resources))) {
                    Log.warning("Minecraft render resources changed while loading; cache not saved");
                    return;
                }
                write(file, identity, capture(loader));
                Log.info("Saved Minecraft render resource cache: " + file + " (" + Files.size(file) + " bytes)");
            } catch (IOException | RuntimeException e) {
                Log.warning("Cannot save Minecraft render resource cache: " + e.getMessage());
            }
        }
    }

    static Path cacheFile(Path dataFolder, String directory) {
        if (directory.isBlank()) throw new IllegalArgumentException("render-cache/directory must not be blank");
        return dataFolder.resolve(Path.of(directory)).toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    static String fingerprint(MinecraftResourceProvider resources) throws IOException {
        MessageDigest digest = digest();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            out.writeInt(FORMAT);
            out.writeUTF(resources.fingerprint());
            // Hash actual code and bundled resources, rather than trusting version labels or timestamps.
            var origins = new LinkedHashSet<Path>();
            try {
                origins.add(Path.of(MinecraftModelLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
                origins.add(Path.of(DynmapBlockState.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
                URL marker = MinecraftModelLoader.class.getResource("/minecraft-model-layers/chest.json");
                if (marker == null) throw new IOException("Missing bundled model-layer resources");
                if (marker.openConnection() instanceof JarURLConnection jar) {
                    origins.add(Path.of(jar.getJarFileURL().toURI()));
                } else {
                    origins.add(Path.of(marker.toURI()).getParent().getParent());
                }
            } catch (java.net.URISyntaxException | IllegalArgumentException | NullPointerException e) {
                throw new IOException("Cannot identify AirMap code/resources for cache validation", e);
            }
            for (Path origin : origins) {
                if (Files.isDirectory(origin)) {
                    try (var paths = Files.walk(origin)) {
                        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                            out.writeUTF(origin.relativize(path).toString().replace('\\', '/'));
                            out.write(hashFile(path));
                        }
                    }
                } else {
                    out.write(hashFile(origin));
                }
            }
            // Include registration order and all properties used by baking or tint/opacity selection.
            for (int i = 0; i < DynmapBlockState.getGlobalIndexMax(); i++) {
                DynmapBlockState state = DynmapBlockState.getStateByGlobalIndex(i);
                out.writeUTF(state.blockName);
                out.writeUTF(state.stateName);
                out.writeInt(state.stateIndex);
                out.writeInt(state.getLightAttenuation());
                out.writeBoolean(state.isWaterFilled());
                out.writeBoolean(state.isLeaves());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static byte[] hashFile(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    record Group(List<int[]> alternatives, List<Integer> weights) { }
    record State(String block, String properties, boolean occluding, int[] textures,
                 TexturePack.BlockTransparency transparency, List<Group> groups) { }
    record Snapshot(Map<String, Integer> textures, List<PatchDefinition> patches, List<State> states) {
        void install(MinecraftResourceProvider resources) throws IOException {
            var loader = new MinecraftModelLoader(resources, HDBlockModels.pdf);
            loader.restoreTextures(textures);
            for (State saved : states) {
                DynmapBlockState state = DynmapBlockState.getStateByNameAndState(saved.block, saved.properties);
                if (!state.blockName.equals(saved.block) || !state.stateName.equals(saved.properties)) {
                    throw new IOException("Cached block state is unavailable: " + saved.block + "[" + saved.properties + "]");
                }
                var groups = new ArrayList<HDBlockPatchModel.WeightedPatchGroup>();
                for (Group group : saved.groups) {
                    var alternatives = new ArrayList<PatchDefinition[]>();
                    for (int[] indices : group.alternatives) {
                        PatchDefinition[] values = new PatchDefinition[indices.length];
                        for (int i = 0; i < values.length; i++) values[i] = patches.get(indices[i]);
                        alternatives.add(values);
                    }
                    groups.add(new HDBlockPatchModel.WeightedPatchGroup(alternatives, group.weights));
                }
                new HDBlockPatchModel(state, groups, "minecraft-json", saved.occluding);
                TexturePack.registerMinecraftState(state, saved.textures, saved.transparency);
            }
            // Dynamic renderers keep their runtime initialization and never enter the on-disk snapshot.
            loader.registerFluids();
        }
    }

    static Snapshot capture(MinecraftModelLoader loader) throws IOException {
        var patchIds = new LinkedHashMap<PatchDefinition, Integer>();
        var states = new ArrayList<State>();
        for (int index = 0; index < HDBlockModels.models_by_id_data.length; index++) {
            if (!(HDBlockModels.models_by_id_data[index] instanceof HDBlockPatchModel model)) continue;
            DynmapBlockState state = DynmapBlockState.getStateByGlobalIndex(index);
            var groups = new ArrayList<Group>();
            for (var group : model.getWeightedGroups()) {
                var alternatives = new ArrayList<int[]>();
                for (PatchDefinition[] patches : group.alternatives()) alternatives.add(patchIndices(patches, patchIds));
                groups.add(new Group(alternatives, group.weights()));
            }
            if (groups.isEmpty()) {
                groups.add(new Group(List.of(patchIndices(model.getPatches(), patchIds)), List.of(1)));
            }
            HDBlockStateTextureMap textures = HDBlockStateTextureMap.getByBlockState(state);
            if (textures == HDBlockStateTextureMap.BLANK) throw new IOException("Missing model texture mapping");
            states.add(new State(state.blockName, state.stateName, model.isOccluding(),
                    textures.faces.clone(), textures.trans, groups));
        }
        return new Snapshot(loader.registeredTextures(), new ArrayList<>(patchIds.keySet()), states);
    }

    private static int[] patchIndices(PatchDefinition[] patches, Map<PatchDefinition, Integer> ids) throws IOException {
        int[] result = new int[patches.length];
        for (int i = 0; i < patches.length; i++) {
            if (patches[i] == null) throw new IOException("Cannot cache a null patch");
            result[i] = ids.computeIfAbsent(patches[i], ignored -> ids.size());
        }
        return result;
    }

    static void write(Path file, String identity, Snapshot snapshot) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "models-", ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT);
                out.writeUTF(identity);
                out.writeInt(snapshot.textures.size());
                for (var entry : snapshot.textures.entrySet()) {
                    out.writeUTF(entry.getKey()); out.writeInt(entry.getValue());
                }
                out.writeInt(snapshot.patches.size());
                for (PatchDefinition patch : snapshot.patches) writePatch(out, patch);
                out.writeInt(snapshot.states.size());
                for (State state : snapshot.states) {
                    out.writeUTF(state.block); out.writeUTF(state.properties);
                    out.writeBoolean(state.occluding);
                    writeInts(out, state.textures);
                    out.writeUTF(state.transparency.name());
                    out.writeInt(state.groups.size());
                    for (Group group : state.groups) {
                        out.writeInt(group.alternatives.size());
                        for (int i = 0; i < group.alternatives.size(); i++) {
                            out.writeInt(group.weights.get(i));
                            writeInts(out, group.alternatives.get(i));
                        }
                    }
                }
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    static Snapshot read(Path file, String identity, PatchDefinitionFactory factory) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT || !in.readUTF().equals(identity)) return null;
            var textures = new LinkedHashMap<String, Integer>();
            int textureCount = count(in);
            for (int i = 0; i < textureCount; i++) {
                String name = in.readUTF();
                if (textures.put(name, in.readInt()) != null) throw new IOException("Duplicate cached texture");
            }
            var patches = new ArrayList<PatchDefinition>();
            int patchCount = count(in);
            for (int i = 0; i < patchCount; i++) patches.add(readPatch(in, factory));
            var states = new ArrayList<State>();
            int stateCount = count(in);
            for (int i = 0; i < stateCount; i++) {
                String block = in.readUTF(), properties = in.readUTF();
                boolean occluding = in.readBoolean();
                int[] faces = readInts(in);
                var transparency = TexturePack.BlockTransparency.valueOf(in.readUTF());
                var groups = new ArrayList<Group>();
                int groupCount = count(in);
                if (groupCount == 0) throw new IOException("Missing cached model groups");
                for (int g = 0; g < groupCount; g++) {
                    var alternatives = new ArrayList<int[]>();
                    var weights = new ArrayList<Integer>();
                    int alternativeCount = count(in);
                    if (alternativeCount == 0) throw new IOException("Missing cached alternatives");
                    int totalWeight = 0;
                    for (int a = 0; a < alternativeCount; a++) {
                        int weight = in.readInt();
                        if (weight <= 0) throw new IOException("Invalid cached model weight");
                        totalWeight = Math.addExact(totalWeight, weight);
                        weights.add(weight);
                        int[] indices = readInts(in);
                        for (int index : indices) {
                            if (index < 0 || index >= patches.size()) throw new IOException("Invalid cached patch reference");
                            if (patches.get(index).textureindex >= faces.length) throw new IOException("Invalid cached face reference");
                        }
                        alternatives.add(indices);
                    }
                    groups.add(new Group(alternatives, weights));
                }
                states.add(new State(block, properties, occluding, faces, transparency, groups));
            }
            // Reading through EOF verifies the gzip checksum before anything is installed.
            if (in.read() != -1) throw new IOException("Trailing render cache data");
            return new Snapshot(textures, patches, states);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IOException("Invalid render cache data", e);
        }
    }

    private static int count(DataInputStream in) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > MAX_ENTRIES) throw new IOException("Invalid render cache count");
        return value;
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }

    private static int[] readInts(DataInputStream in) throws IOException {
        int[] values = new int[count(in)];
        for (int i = 0; i < values.length; i++) values[i] = in.readInt();
        return values;
    }

    private static void writePatch(DataOutputStream out, PatchDefinition p) throws IOException {
        for (double value : new double[] {p.x0,p.y0,p.z0,p.xu,p.yu,p.zu,p.xv,p.yv,p.zv,
                p.umin,p.umax,p.vmin,p.vmax,p.vminatumax,p.vmaxatumax}) out.writeDouble(value);
        out.writeUTF(p.sidevis.name());
        out.writeInt(p.textureindex);
        out.writeBoolean(p.shade);
        out.writeUTF(p.shadeStep == null ? "" : p.shadeStep.name());
    }

    private static PatchDefinition readPatch(DataInputStream in, PatchDefinitionFactory factory) throws IOException {
        double[] v = new double[15];
        for (int i = 0; i < v.length; i++) {
            v[i] = in.readDouble();
            if (!Double.isFinite(v[i])) throw new IOException("Non-finite cached patch coordinate");
        }
        SideVisible side = SideVisible.valueOf(in.readUTF());
        int texture = in.readInt();
        if (texture < 0) throw new IOException("Invalid cached patch texture");
        boolean shade = in.readBoolean();
        String step = in.readUTF();
        PatchDefinition patch = factory.getPatch(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],
                v[9],v[10],v[11],v[12],side,texture,v[13],v[14],shade,step.isEmpty() ? null : BlockStep.valueOf(step));
        if (patch == null) throw new IOException("Invalid cached patch geometry");
        return patch;
    }
}
