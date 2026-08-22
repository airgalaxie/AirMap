package org.dynmap.bukkit.helper;

import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.Polygon;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;


/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelper {
	public static BukkitVersionHelper helper = null;

	public static GenericChunkCache gencache;

    private static Registry<Biome> reg = null;

    private static Registry<Biome> getBiomeReg() {
    	if (reg == null) {
    		reg = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);
    	}
    	return reg;
    }

    private Object[] biomelist;

	public BukkitVersionHelper() {
		helper = this;
	}

    /**
     * Get list of defined biomebase objects
     */
    public Object[] getBiomeBaseList() {
    	if (biomelist == null) {
        	biomelist = new Biome[256];
        	Iterator<Biome> iter = getBiomeReg().iterator();
        	while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = getBiomeReg().getId(b);
        		if (bidx >= biomelist.length) {
        			biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
        		}
        		biomelist[bidx] = b;
        	}
        }
        return biomelist;
    }

    /** Get ID from biomebase */
    public int getBiomeBaseID(Object bb) {
    	return getBiomeReg().getId((Biome)bb);
    }


    public static IdentityHashMap<BlockState, DynmapBlockState> dataToState;

    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    public void initializeBlockStates() {
    	dataToState = new IdentityHashMap<BlockState, DynmapBlockState>();
    	HashMap<String, DynmapBlockState> lastBlockState = new HashMap<String, DynmapBlockState>();
    	IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;
        Block baseb = null;
    	Iterator<BlockState> iter = bsids.iterator();
    	ArrayList<String> names = new ArrayList<String>();

    	// Loop through block data states
    	DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
		while (iter.hasNext()) {
    		BlockState bd = iter.next();
    		Block b = bd.getBlock();
        	Identifier id = BuiltInRegistries.BLOCK.getKey(b);
    		String bname = id.toString();
    		DynmapBlockState lastbs = lastBlockState.get(bname);	// See if we have seen this one
    		int idx = 0;
    		if (lastbs != null) {	// Yes
    			idx = lastbs.getStateCount();	// Get number of states so far, since this is next
    		}
    		// Build state name
    		String sb = "";
    		String fname = bd.toString();
    		int off1 = fname.indexOf('[');
    		if (off1 >= 0) {
    			int off2 = fname.indexOf(']');
    			sb = fname.substring(off1+1, off2);
    		}
            int lightAtten = bd.getLightDampening();	// getLightDampening
            //Log.info("statename=" + bname + "[" + sb + "], lightAtten=" + lightAtten);
            // Fill in base attributes
            bld.setBaseState(lastbs).setStateIndex(idx).setBlockName(bname).setStateName(sb).setAttenuatesLight(lightAtten);
			bld.setMaterial(bd.getSoundType().toString());
			if (bd.isSolid()) { bld.setSolid(); }
            if (bd.isAir()) { bld.setAir(); }
            if (bd.is(BlockTags.OVERWORLD_NATURAL_LOGS)) { bld.setLog(); }
            if (bd.is(BlockTags.LEAVES)) { bld.setLeaves(); }
            if ((!bd.getFluidState().isEmpty()) && (!(bd.getBlock() instanceof LiquidBlock))) {	// Test if fluid type for block is not empty
				bld.setWaterlogged();
				//Log.info("statename=" + bname + "[" + sb + "] = waterlogged");
			}
            DynmapBlockState dbs = bld.build(); // Build state

    		dataToState.put(bd,  dbs);
    		lastBlockState.put(bname, (lastbs == null) ? dbs : lastbs);
    		Log.verboseinfo("blk=" + bname + ", idx=" + idx + ", state=" + sb + ", waterlogged=" + dbs.isWaterlogged());
    	}
    }

    /**
     * Create chunk cache for given chunks of given world
     * @param dw - world
     * @param chunks - chunk list
     * @return cache
     */
    public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
        MapChunkCache c = new MapChunkCache(gencache);
        c.setChunks(dw, chunks);
        return c;
    }

	/**
	 * Get biome base water multiplier
	 */
	public int getBiomeBaseWaterMult(Object bb) {
    	Biome biome = (Biome) bb;
    	return biome.getWaterColor();	// waterColor
	}

    /** Get temperature from biomebase */
    public float getBiomeBaseTemperature(Object bb) {
    	return ((Biome)bb).getBaseTemperature();
    }

    /** Get humidity from biomebase */
    public float getBiomeBaseHumidity(Object bb) {
    	String vals = ((Biome)bb).climateSettings.toString();	// Sleazy
    	float humidity = 0.5F;
    	int idx = vals.indexOf("downfall=");
    	if (idx >= 0) {
        	humidity = Float.parseFloat(vals.substring(idx+9, vals.indexOf(']', idx)));
    	}
    	return humidity;
    }

    public Polygon getWorldBorder(World world) {
        Polygon p = null;
        WorldBorder wb = world.getWorldBorder();
        if (wb != null) {
        	Location c = wb.getCenter();
        	double size = wb.getSize();
        	if ((size > 1) && (size < 1E7)) {
        	    size = size / 2;
        		p = new Polygon();
        		p.addVertex(c.getX()-size, c.getZ()-size);
        		p.addVertex(c.getX()+size, c.getZ()-size);
        		p.addVertex(c.getX()+size, c.getZ()+size);
        		p.addVertex(c.getX()-size, c.getZ()+size);
        	}
        }
        return p;
    }
	// Send title/subtitle to user
    public void sendTitleText(Player p, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
    	if (p != null) {
    		p.sendTitle(title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
    	}
    }

	private String[] biomenames;
	public String[] getBiomeNames() {
    	if (biomenames == null) {
        	biomenames = new String[256];
        	Iterator<Biome> iter = getBiomeReg().iterator();
        	while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = getBiomeReg().getId(b);
        		if (bidx >= biomenames.length) {
        			biomenames = Arrays.copyOf(biomenames, bidx + biomenames.length);
        		}
        		biomenames[bidx] = b.toString();
        	}
        }
        return biomenames;
	}

    /** Get ID string from biomebase */
    public String getBiomeBaseIDString(Object bb) {
		return getBiomeReg().getKey((Biome)bb).getPath();
    }
    public String getBiomeBaseResourceLocsation(Object bb) {
        return getBiomeReg().getKey((Biome)bb).toString();
	}

	public Player[] getOnlinePlayers() {
        Collection<? extends Player> p = Bukkit.getServer().getOnlinePlayers();
        return p.toArray(new Player[0]);
	}

    /**
     * Get skin URL for player
     * @param player
     */
    public String getSkinURL(Player player) {
		URL url = player.getPlayerProfile().getTextures().getSkin();

		return url != null ? url.toString() : null;
    }
	// Get minY for world
	public int getWorldMinY(World w) {
		CraftWorld cw = (CraftWorld) w;
		return cw.getMinHeight();
	}
}
