package org.dynmap.bukkit.helper;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.craftbukkit.CraftWorld;
import org.dynmap.MapManager;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The provider used to work with paper libs
 */
public class AsyncChunkProvider {
    private int currTick = MinecraftServer.currentTick;
    private int currChunks = 0;

    public CompletableFuture<CompoundTag> getChunk(ServerLevel world, int x, int y) throws InvocationTargetException, IllegalAccessException {
        CompletableFuture<CompoundTag> future = new CompletableFuture<>();
        MoonriseRegionFileIO.loadDataAsync(world, x, y, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
                                           (nbt, exception) -> future.complete(nbt), true,
                                           Priority.LOWEST);
        return future;
    }

    public synchronized Supplier<CompoundTag> getLoadedChunk(CraftWorld world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) return () -> null;
        LevelChunk c = world.getHandle().getChunkIfLoaded(x, z);
        if (c == null) return () -> null;
        if (currTick != MinecraftServer.currentTick) {
            currTick = MinecraftServer.currentTick;
            currChunks = 0;
        }
        //we shouldn't stress main thread
        if (++currChunks > MapManager.mapman.getMaxChunkLoadsPerTick()) {
            try {
                Thread.sleep(25); //hold the lock so other threads also won't stress main thread
            } catch (InterruptedException ignored) {}
        }
        CompoundTag nbt = SerializableChunkData.copyOf(world.getHandle(), c).write();
        return () -> nbt;
    }
}
