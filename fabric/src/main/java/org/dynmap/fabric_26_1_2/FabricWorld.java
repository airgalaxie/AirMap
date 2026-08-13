package org.dynmap.fabric_26_1_2;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class FabricWorld extends DynmapWorld {
    // TODO: Store this relative to Level saves for integrated server
    public static final String SAVED_WORLDS_FILE = "fabricworlds.yml";

    private final DynmapPlugin plugin;
    private Level world;
    private final boolean skylight;
    private final boolean isnether;
    private final boolean istheend;
    private final String env;
    private List<String> legacyNames;
    private DynmapLocation spawnloc = new DynmapLocation();
    private static int maxLevelHeight = 320;    // Maximum allows world height

    public static int getMaxLevelHeight() {
        return maxLevelHeight;
    }

    public static void setMaxLevelHeight(int h) {
        maxLevelHeight = h;
    }

    public static String getLevelName(Level w) {
        return w.dimension().identifier().toString();
    }

    private static List<String> getLegacyLevelNames(Level w) {
        ResourceKey<Level> dimension = w.dimension();
        List<String> aliases = new ArrayList<String>();
        if (dimension.equals(Level.OVERWORLD)) {
            aliases.add(w.getServer().getWorldData().getLevelName());
            aliases.add("world");
        } else if (dimension.equals(Level.NETHER)) {
            aliases.add("DIM-1");
            aliases.add("nether");
        } else if (dimension.equals(Level.END)) {
            aliases.add("DIM1");
            aliases.add("the_end");
        } else {
            aliases.add(dimension.identifier().getNamespace() + "_" + dimension.identifier().getPath());
        }
        aliases.removeIf(getLevelName(w)::equals);
        return Collections.unmodifiableList(aliases);
    }
    
    public void updateLevel(Level w) {
    	this.updateWorldHeights(w.getHeight(), w.getMinY(), w.getSeaLevel());
    }

    public FabricWorld(DynmapPlugin plugin, Level w) {
        this(plugin, getLevelName(w), w.getHeight(),
                w.getSeaLevel(),
                w.dimension() == Level.NETHER,
                w.dimension() == Level.END,
                w.dimension().identifier().getPath(),
                w.getMinY());
        legacyNames = getLegacyLevelNames(w);
        setLevelLoaded(w);
    }

    public FabricWorld(DynmapPlugin plugin, String name, int height, int sealevel, boolean nether, boolean the_end, String deftitle, int miny) {
        super(name, (height > maxLevelHeight) ? maxLevelHeight : height, sealevel, miny);
        this.plugin = plugin;
        world = null;
        setTitle(deftitle);
        isnether = nether;
        istheend = the_end;
        skylight = !(isnether || istheend);

        if (isnether) {
            env = "nether";
        } else if (istheend) {
            env = "the_end";
        } else {
            env = "normal";
        }

        legacyNames = Collections.emptyList();

    }

    @Override
    public List<String> getNameAliases() {
        return legacyNames;
    }

    /* Test if world is nether */
    @Override
    public boolean isNether() {
        return isnether;
    }

    public boolean isTheEnd() {
        return istheend;
    }

    /* Get world spawn location */
    @Override
    public DynmapLocation getSpawnLocation() {
        if (world != null) {
            BlockPos spawnPos = world.getRespawnData().pos();
            spawnloc.x = spawnPos.getX();
            spawnloc.y = spawnPos.getY();
            spawnloc.z = spawnPos.getZ();
            spawnloc.world = this.getName();
        }
        return spawnloc;
    }

    /* Get world time */
    @Override
    public long getTime() {
        if (world != null)
            return world.getGameTime();
        else
            return -1;
    }

    /* Level is storming */
    @Override
    public boolean hasStorm() {
        if (world != null)
            return world.isRaining();
        else
            return false;
    }

    /* Level is thundering */
    @Override
    public boolean isThundering() {
        if (world != null)
            return world.isThundering();
        else
            return false;
    }

    /* Level is loaded */
    @Override
    public boolean isLoaded() {
        return (world != null);
    }

    /* Set world to unloaded */
    @Override
    public void setWorldUnloaded() {
        getSpawnLocation();
        world = null;
    }

    /* Set world to loaded */
    public void setLevelLoaded(Level w) {
        world = w;
        this.sealevel = w.getSeaLevel();   // Read actual current sealevel from world
        // Update lighting table
        for (int lightLevel = 0; lightLevel < 16; lightLevel++) {
            // Algorithm based on LightmapTextureManager.getBrightness()
            // We can't call that method because it's client-only.
            // This means the code below can stop being correct if Mojang ever
            // updates the curve; in that case we should reflect the changes.
            float value = (float) lightLevel / 15.0f;
            float brightness = value / (4.0f - 3.0f * value);
            this.setBrightnessTableEntry(lightLevel, Mth.lerp(w.dimensionType().ambientLight(), brightness, 1.0F));
        }
    }

    /* Get light level of block */
    @Override
    public int getLightLevel(int x, int y, int z) {
        if (world != null)
            return world.getMaxLocalRawBrightness(new BlockPos(x, y, z));
        else
            return -1;
    }

    /* Get highest Y coord of given location */
    @Override
    public int getHighestBlockYAt(int x, int z) {
        if (world != null) {
            return world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        } else
            return -1;
    }

    /* Test if sky light level is requestable */
    @Override
    public boolean canGetSkyLightLevel() {
        return skylight;
    }

    /* Return sky light level */
    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        if (world != null) {
            return world.getBrightness(LightLayer.SKY, new BlockPos(x, y, z));
        } else
            return -1;
    }

    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    @Override
    public String getEnvironment() {
        return env;
    }

    /**
     * Get map chunk cache for world
     */
    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks) {
        if (world != null) {
            FabricMapChunkCache c = new FabricMapChunkCache(plugin);
            c.setChunks(this, chunks);
            return c;
        }
        return null;
    }

    public Level getLevel() {
        return world;
    }

    @Override
    public Polygon getWorldBorder() {
        if (world != null) {
            WorldBorder wb = world.getWorldBorder();
            if ((wb != null) && (wb.getSize() < 5.9E7)) {
                Polygon p = new Polygon();
                p.addVertex(wb.getMinX(), wb.getMinZ());
                p.addVertex(wb.getMinX(), wb.getMaxZ());
                p.addVertex(wb.getMaxX(), wb.getMaxZ());
                p.addVertex(wb.getMaxX(), wb.getMinZ());
                return p;
            }
        }
        return null;
    }
}
