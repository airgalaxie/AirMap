package org.dynmap.bukkit.helper;

import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache extends GenericMapChunkCache {
    private static volatile AsyncChunkProvider provider;
    private final AsyncChunkProvider chunkProvider;
    private World w;

    public static void startChunkSnapshots() {
        stopChunkSnapshots();
        provider = new AsyncChunkProvider();
    }

    public static void processChunkSnapshots() {
        AsyncChunkProvider current = provider;
        if (current != null) current.processTick();
    }

    public static void stopChunkSnapshots() {
        AsyncChunkProvider current = provider;
        provider = null;
        if (current != null) current.close();
    }
    /**
     * Construct empty cache
     */
    public MapChunkCache(GenericChunkCache cc) {
        super(cc);
        chunkProvider = Objects.requireNonNull(provider, "Chunk snapshot provider is not running");
    }

    // Load generic chunk from existing and already loaded chunk
    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
        Supplier<CompoundTag> supplier = chunkProvider.getLoadedChunk((CraftWorld) w, chunk.x, chunk.z);
        return () -> {
            CompoundTag nbt = supplier.get();
            return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
        };
    }

    // Load generic chunk from unloaded chunk
    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk){
        try {
            CompletableFuture<CompoundTag> nbt = chunkProvider.getChunk(((CraftWorld) w).getHandle(), chunk.x, chunk.z);
            return () -> {
                CompoundTag compound;
                try {
                    compound = nbt.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Interrupted while reading chunk data");
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                return compound == null ? null : parseChunkFromNBT(new NBT.NBTCompound(compound));
            };
        } catch (InvocationTargetException | IllegalAccessException exception) {
            org.dynmap.Log.severe(String.format("Error requesting chunk: %d,%d", chunk.x, chunk.z), exception);
            return () -> null;
        }
    }

    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        super.setChunks(dw, chunks);
    }
}
