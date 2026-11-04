package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.dynmap.*;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.hdmap.*;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.ZipMinecraftResourceProvider;
import org.dynmap.utils.*;
import org.junit.jupiter.api.*;

/** Offline real-region render: original blocks, stored light, models and textures, real Core ray tracer. */
class RealFlatLightingRenderTest {
    private static final DynmapWorld WORLD = new DynmapWorld("real-flat", 384, 63, -64) {
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return null; }
        @Override public long getTime() { return 0; }
        @Override public boolean hasStorm() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isLoaded() { return true; }
        @Override public void setWorldUnloaded() { }
        @Override public int getLightLevel(int x, int y, int z) { return 15; }
        @Override public int getHighestBlockYAt(int x, int z) { return 64; }
        @Override public boolean canGetSkyLightLevel() { return true; }
        @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
        @Override public String getEnvironment() { return "normal"; }
        @Override public MapChunkCache getChunkCache(List<DynmapChunk> chunks) { return null; }
    };

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object allocate(Class<?> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return ((sun.misc.Unsafe) f.get(null)).allocateInstance(type);
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    /** Real production ray tracer and shaders; no substituted AO or lighting. */
    private static final class RenderFixture {
        final MapChunkCache cache;
        final HDShaderState shader;
        final HDPerspectiveState ps;
        final Method raytrace;
        final Field px, py;
        final Vector3D top = new Vector3D(), bottom = new Vector3D(), direction = new Vector3D();
        final Matrix3D worldToMap, mapToWorld;
        final boolean flat;
        final int originX, originZ;
        final Color color = new Color();
        final boolean[] done = new boolean[1];
        final HDShaderState[] states;

        RenderFixture(MapChunkCache cache, HDMap map, TexturePackHDShader shader) throws Exception {
            this(cache, map, shader, 0, 96);
        }

        RenderFixture(MapChunkCache cache, HDMap map, TexturePackHDShader shader, int originX, int originZ) throws Exception {
            this.cache = cache;
            this.originX = originX;
            this.originZ = originZ;
            IsoHDPerspective projection = (IsoHDPerspective) map.getPerspective();
            flat = projection.inclination == 90;
            worldToMap = (Matrix3D) field(projection, "world_to_map").get(projection);
            mapToWorld = (Matrix3D) field(projection, "map_to_world").get(projection);
            MapIterator iterator = cache.getIterator(0, 300, 96);
            this.shader = shader.getStateInstance(map, cache, iterator, 4);
            Class<?> type = Class.forName(IsoHDPerspective.class.getName() + "$OurPerspectiveState");
            Constructor<?> ctor = type.getDeclaredConstructor(IsoHDPerspective.class, MapIterator.class, boolean.class, int.class);
            ctor.setAccessible(true);
            ps = (HDPerspectiveState) ctor.newInstance(projection, iterator, false, 0);
            raytrace = type.getDeclaredMethod("raytrace", MapChunkCache.class, HDShaderState[].class, boolean[].class);
            raytrace.setAccessible(true);
            field(ps, "top").set(ps, top);
            field(ps, "bottom").set(ps, bottom);
            field(ps, "direction").set(ps, direction);
            px = field(ps, "px"); py = field(ps, "py");
            states = new HDShaderState[] {this.shader};
        }

        void render(BufferedImage image, int ox, int oz, int width, int height) throws Exception {
            for (int x = ox; x < ox + width; x++) for (int z = oz; z < oz + height; z++) {
                double wx = originX + (x + 0.5) / 4, wz = originZ + (z + 0.5) / 4;
                if (flat) {
                    top.x = bottom.x = wx; top.z = bottom.z = wz;
                    top.y = 319.5; bottom.y = -64.5;
                } else {
                    top.x = wx; top.y = 64; top.z = wz;
                    worldToMap.transform(top);
                    bottom.x = top.x; bottom.y = top.y;
                    top.z = 319.5; bottom.z = -64.5;
                    mapToWorld.transform(top); mapToWorld.transform(bottom);
                }
                direction.x = bottom.x - top.x;
                direction.y = bottom.y - top.y;
                direction.z = bottom.z - top.z;
                px.setInt(ps, x - ox); py.setInt(ps, z - oz);
                done[0] = false;
                shader.reset(ps);
                raytrace.invoke(ps, cache, states, done);
                if (!done[0]) shader.rayFinished(ps);
                shader.getRayColor(color, 0);
                image.setRGB(x, z, color.getARGB());
            }
        }
    }

    private static String canonical(String properties) {
        String[] parts = properties.split(",");
        Arrays.sort(parts);
        return String.join(",", parts);
    }

    @Test
    void renderRealTerrainThroughFlatCore() throws Exception {
        String directory = System.getenv("AIRMAP_REAL_FLAT_REGION_DIR");
        String client = System.getenv("AIRMAP_REAL_FLAT_CLIENT_JAR");
        String stateFile = System.getenv("AIRMAP_REAL_FLAT_STATE_FILE");
        String output = System.getenv("AIRMAP_REAL_FLAT_OUTPUT");
        Assumptions.assumeTrue(directory != null && client != null && stateFile != null && output != null,
                "real-region render inputs not requested");
        boolean forest = "forest".equals(System.getenv("AIRMAP_REAL_FLAT_FIXTURE"));
        int minChunkX = forest ? 119 : -5, maxChunkX = forest ? 122 : 8;
        int minChunkZ = forest ? 123 : 5, maxChunkZ = forest ? 126 : 14;
        int originX = forest ? 1920 : 0, originZ = forest ? 1984 : 96;
        int width = forest ? 128 : 256, height = forest ? 128 : 512;
        int landscapeX = forest ? originX : -64, landscapeWidth = forest ? 128 : 512;
        MapManager original = MapManager.mapman;
        try {
            Path out = Path.of(output);
            Files.createDirectories(out);
            Method readChunk = Real263ChunkParseTest.class.getDeclaredMethod("readRegionChunk", String.class, int.class, int.class);
            Method readRoot = Real263ChunkParseTest.class.getDeclaredMethod("readRoot", byte[].class);
            readChunk.setAccessible(true);
            readRoot.setAccessible(true);
            Map<DynmapChunk, GenericNBTCompound> roots = new LinkedHashMap<>();
            Set<String> needed = new HashSet<>();
            // Read the real region and context around the 64x128 block terrain view.
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    Path region = Path.of(directory, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
                    byte[] bytes = (byte[]) readChunk.invoke(null, region.toString(), cx, cz);
                    assertNotNull(bytes, "Missing real chunk " + cx + "," + cz);
                    @SuppressWarnings("unchecked")
                    var raw = (Map<String,Object>) readRoot.invoke(null, bytes);
                    GenericNBTCompound root = new Real263ChunkParseTest.MapCompound(raw);
                    roots.put(new DynmapChunk(cx, cz), root);
                    GenericNBTList sections = root.getList("sections", 10);
                    for (int s = 0; s < sections.size(); s++) {
                        GenericNBTList palette = sections.getCompound(s).getCompound("block_states").getList("palette", 10);
                        for (int p = 0; p < palette.size(); p++) {
                            GenericNBTCompound entry = palette.getCompound(p);
                            String name = entry.getString(entry.contains("id") ? "id" : "Name");
                            var props = entry.getCompound(entry.contains("properties") ? "properties" : "Properties");
                            List<String> values = new ArrayList<>();
                            for (String key : props.getAllKeys()) values.add(key + "=" + props.getAsString(key));
                            needed.add(name + "\t" + canonical(String.join(",", values)));
                        }
                    }
                }
            }
            // Flags come from the local Minecraft registry, not guessed from block names.
            Map<String, DynmapBlockState> bases = new HashMap<>();
            int registered = 0;
            List<String> registry = new ArrayList<>(Files.readAllLines(Path.of(stateFile)));
            registry.sort(Comparator.comparing(line -> !line.endsWith("\ttrue")));
            for (String line : registry) {
                String[] fields = line.split("\t", -1);
                boolean requested = needed.remove(fields[0] + "\t" + canonical(fields[1]));
                if (fields[4].equals("true")) requested |= needed.remove(fields[0] + "\t");
                if (!requested) continue;
                if (fields[0].equals("minecraft:air")) continue;
                DynmapBlockState base = bases.get(fields[0]);
                DynmapBlockState.Builder builder = new DynmapBlockState.Builder().setBlockName(fields[0])
                        .setStateName(fields[1]).setAttenuatesLight(Integer.parseInt(fields[2]));
                if (base != null) builder.setBaseState(base).setStateIndex(base.getStateCount());
                if (Boolean.parseBoolean(fields[3])) builder.setAir();
                if (fields[0].endsWith("_leaves")) builder.setLeaves();
                if (fields[1].contains("waterlogged=true")) builder.setWaterlogged();
                DynmapBlockState state = builder.build();
                bases.putIfAbsent(fields[0], state);
                registered++;
            }
            assertTrue(needed.isEmpty(), "Registry lacks real palette states: " + needed);
            GenericMapChunkCache cache = new GenericMapChunkCache(new GenericChunkCache(128, false)) {
                @Override public boolean isEmptySection(int x, int y, int z) { return false; }
            };
            List<DynmapChunk> chunks = new ArrayList<>(roots.keySet());
            cache.setChunks(WORLD, chunks);
            Field snapshots = GenericMapChunkCache.class.getDeclaredField("snaparray");
            snapshots.setAccessible(true);
            GenericChunk[] data = (GenericChunk[]) snapshots.get(cache);
            for (var entry : roots.entrySet()) {
                DynmapChunk c = entry.getKey();
                data[(c.x - minChunkX) + (c.z - minChunkZ) * (maxChunkX - minChunkX + 1)] = cache.parseChunkFromNBT(entry.getValue());
            }
            DynmapCore core = new DynmapCore();
            core.setDataFolder(out.resolve("resource-cache").toFile());
            Files.createDirectories(core.getDataFolder().toPath());
            set(core, "minecraftResources", new ZipMinecraftResourceProvider(Path.of(client)));
            Class<?> serverType = Class.forName("org.dynmap.hdmap.ChestFlatVsSurfaceRenderTest$StubServer");
            Constructor<?> serverCtor = serverType.getDeclaredConstructor();
            serverCtor.setAccessible(true);
            core.setServer((DynmapServerInterface) serverCtor.newInstance());
            MapManager manager = (MapManager) allocate(MapManager.class);
            set(manager, "core", core);
            set(manager, "useBrightnessTable", true);
            set(core, "smoothlighting", true);
            MapManager.mapman = manager;
            HDBlockModels.loadModels(core, new ConfigurationNode());
            StringBuilder metrics = new StringBuilder("region=" + directory + "\nchunks=" + roots.size()
                    + "\nregistryStates=" + registered + "\nproductionClasses="
                    + IsoHDPerspective.class.getProtectionDomain().getCodeSource().getLocation() + "\n");
            for (int inclination : new int[] {90, 60}) {
                ConfigurationNode projectionConfig = new ConfigurationNode();
                projectionConfig.put("name", "real-" + inclination);
                projectionConfig.put("inclination", inclination);
                projectionConfig.put("azimuth", 180);
                projectionConfig.put("scale", 4);
                IsoHDPerspective projection = new IsoHDPerspective(core, projectionConfig);
                ConfigurationNode lightConfig = new ConfigurationNode();
                lightConfig.put("name", "shadows");
                lightConfig.put("shadowstrength", 1.0);
                HDMap map = (HDMap) allocate(HDMap.class);
                set(map, "perspective", projection);
                set(map, "lighting", new ShadowHDLighting(core, lightConfig));
                ConfigurationNode shaderConfig = new ConfigurationNode();
                shaderConfig.put("texturepack", "standard");
                TexturePackHDShader shader = new TexturePackHDShader(core, shaderConfig);
                RenderFixture fixture = new RenderFixture(cache, map, shader, originX, originZ);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                // Warm both the JVM and the texture/model caches before measurement.
                for (int warmup = 0; warmup < 3; warmup++) fixture.render(image, 0, 0, width, height);
                var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
                long thread = Thread.currentThread().threadId();
                for (int repeat = 0; repeat < 7; repeat++) {
                    long allocated = bean.getThreadAllocatedBytes(thread);
                    long cpu = bean.getCurrentThreadCpuTime();
                    long wall = System.nanoTime();
                    fixture.render(image, 0, 0, width, height);
                    wall = System.nanoTime() - wall;
                    cpu = bean.getCurrentThreadCpuTime() - cpu;
                    allocated = bean.getThreadAllocatedBytes(thread) - allocated;
                    metrics.append("render_").append(inclination).append('_').append(repeat)
                            .append("=wall_ns:").append(wall).append(",cpu_ns:").append(cpu)
                            .append(",allocated_bytes:").append(allocated).append('\n');
                }
                long opaque = 0;
                for (int x = 0; x < width; x++) for (int z = 0; z < height; z++) {
                    if ((image.getRGB(x, z) >>> 24) == 255) opaque++;
                }
                assertTrue(opaque > 1000, "Real terrain must be visible");
                metrics.append("opaque_").append(inclination).append('=').append(opaque).append('\n');
                ImageIO.write(image, "png", out.resolve("real-" + inclination + ".png").toFile());
                if (inclination == 90) {
                    BufferedImage landscape = new BufferedImage(landscapeWidth, height, BufferedImage.TYPE_INT_ARGB);
                    new RenderFixture(cache, map, shader, landscapeX, originZ).render(landscape, 0, 0, landscapeWidth, height);
                    ImageIO.write(landscape, "png", out.resolve("landscape-90.png").toFile());
                    // Recreate each 32x32-block tile with its independently loaded
                    // production chunk footprint, rather than sharing the full scene cache.
                    BufferedImage tiled = new BufferedImage(landscapeWidth, height, BufferedImage.TYPE_INT_ARGB);
                    Matrix3D worldToMap = (Matrix3D) field(projection, "world_to_map").get(projection);
                    for (int ox = 0; ox < landscapeWidth; ox += 128) for (int oz = 0; oz < height; oz += 128) {
                        Vector3D center = new Vector3D(landscapeX + (ox + 64) / 4.0, 64, originZ + (oz + 64) / 4.0);
                        worldToMap.transform(center);
                        HDMapTile tile = new HDMapTile(WORLD, projection,
                                (int) Math.floor(center.x / 128), (int) Math.floor(center.y / 128), 0, 0);
                        List<DynmapChunk> required = projection.getRequiredChunks(tile);
                        GenericMapChunkCache tileCache = new GenericMapChunkCache(new GenericChunkCache(32, false)) {
                            @Override public boolean isEmptySection(int x, int y, int z) { return false; }
                        };
                        tileCache.setChunks(WORLD, required);
                        int minX = field(tileCache, "x_min").getInt(tileCache);
                        int minZ = field(tileCache, "z_min").getInt(tileCache);
                        int dim = field(tileCache, "x_dim").getInt(tileCache);
                        GenericChunk[] snapshotsForTile = (GenericChunk[]) field(tileCache, "snaparray").get(tileCache);
                        for (DynmapChunk chunk : required) {
                            assertTrue(roots.containsKey(chunk), "Fixture needs chunk " + chunk.x + "," + chunk.z);
                            snapshotsForTile[chunk.x - minX + (chunk.z - minZ) * dim] = cache.parseChunkFromNBT(roots.get(chunk));
                        }
                        new RenderFixture(tileCache, map, shader, landscapeX, originZ).render(tiled, ox, oz, 128, 128);
                    }
                    for (int x = 0; x < landscapeWidth; x++) for (int z = 0; z < height; z++) {
                        assertEquals(landscape.getRGB(x, z), tiled.getRGB(x, z),
                                "Independent tile context must match full render at " + x + "," + z);
                    }
                    ImageIO.write(tiled, "png", out.resolve("real-90-tiled.png").toFile());
                    metrics.append("independent_tile_pixel_mismatches=0\n");
                }
            }
            Files.writeString(out.resolve("real-render.txt"), metrics);
        } finally {
            MapManager.mapman = original;
        }
    }
}
