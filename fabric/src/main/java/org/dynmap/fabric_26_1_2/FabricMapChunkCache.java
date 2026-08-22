package org.dynmap.fabric_26_1_2;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.*;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class FabricMapChunkCache extends GenericMapChunkCache {
    private Level w;
    private ServerChunkCache cps;

    /**
     * Construct empty cache
     */
    public FabricMapChunkCache(DynmapPlugin plugin) {
    	super(plugin.sscache);
    }

    public void setChunks(FabricWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getLevel();
        if (dw.isLoaded()) {
            /* Check if world's provider is ServerChunkCache */
            ChunkSource cp = this.w.getChunkSource();

            if (cp instanceof ServerChunkCache) {
                cps = (ServerChunkCache) cp;
            } else {
                Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
            }
        } 
        super.setChunks(dw, chunks);
    }

    // Load generic chunk from existing and already loaded chunk
    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
        return () -> getLoadedChunk(chunk);
    }

    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        GenericChunk gc = null;
        if ((cps != null) && cps.hasChunk(chunk.x, chunk.z)) {
            CompoundTag nbt = null;
            try {
                LevelChunk levelChunk = cps.getChunkNow(chunk.x, chunk.z);
                if (levelChunk != null) {
                    nbt = SerializableChunkData.copyOf((ServerLevel) w, levelChunk).write();
                }
            } catch (NullPointerException e) {
                Log.severe("SerializableChunkData.copyOf threw a NullPointerException", e);
            }
            if (nbt != null) {
            	gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
            }
        }
        return gc;
    }

    private CompoundTag readChunk(int x, int z) {
        try {
            ChunkPos coord = new ChunkPos(x, z);
            // Async chunk reading is synchronized here. Perhaps we can do async and improve performance?
            return cps.chunkMap.read(coord).join().orElse(null);
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %d,%d", x, z), exc);
            return null;
        }
    }

    // Load generic chunk from unloaded chunk
    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk) {
        return () -> loadChunk(chunk);
    }

    protected GenericChunk loadChunk(DynmapChunk chunk) {
        GenericChunk gc = null;
        CompoundTag nbt = readChunk(chunk.x, chunk.z);
        // If read was good
        if (nbt != null) {
            gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
        }
        return gc;
    }

    @Override
    public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
        return bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).flatMap(BiomeSpecialEffects::foliageColorOverride).orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeSpecialEffects effects = bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).orElse(null);
        if (effects == null) return colormap[bm.biomeLookup()];
        return effects.grassColorModifier().modifyColor(x, z, effects.grassColorOverride().orElse(colormap[bm.biomeLookup()]));
    }
}
