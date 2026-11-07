package org.dynmap.bukkit.helper;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Tick-thread snapshots for loaded Paper chunks and asynchronous region-file reads. */
public class AsyncChunkProvider implements AutoCloseable {
    // A soft budget: an individual copy cannot be preempted, but another is not
    // started once this much tick time has been spent across all render workers.
    private final TickSnapshotQueue<SerializableChunkData> snapshots =
            new TickSnapshotQueue<>(Bukkit::isPrimaryThread, System::nanoTime);

    public void processTick() {
        snapshots.runTick(Math.max(1, MapManager.mapman.getMaxChunkLoadsPerTick()),
                MapManager.mapman.getRenderDataBudgetNanos());
    }

    @Override
    public void close() {
        snapshots.close();
    }

    public CompletableFuture<CompoundTag> getChunk(ServerLevel world, int x, int y) throws InvocationTargetException, IllegalAccessException {
        CompletableFuture<CompoundTag> future = new CompletableFuture<>();
        MoonriseRegionFileIO.loadDataAsync(world, x, y, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
                                           (nbt, exception) -> {
                                               if (exception != null) {
                                                   Log.severe("Error reading chunk: " + x + "," + y, exception);
                                                   future.completeExceptionally(exception);
                                               } else {
                                                   future.complete(nbt);
                                               }
                                           }, true,
                                           Priority.LOWEST);
        return future;
    }

    public Supplier<CompoundTag> getLoadedChunk(CraftWorld world, int x, int z) {
        return snapshots.request(() -> {
            // Resolve again on the owning thread: the world/chunk may have
            // unloaded since the render request. Never load or generate a chunk.
            if (Bukkit.getWorld(world.getUID()) != world || !world.isChunkLoaded(x, z)) return null;
            ServerLevel level = world.getHandle();
            LevelChunk chunk = level.getChunkIfLoaded(x, z);
            return chunk == null ? null : SerializableChunkData.copyOf(level, chunk);
        }, data -> {
            // Encoding is deliberately deferred to the consuming render
            // thread, rather than a future callback on the tick thread.
            return data == null ? null : data.write();
        });
    }
}
