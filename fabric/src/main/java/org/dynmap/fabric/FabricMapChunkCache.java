package org.dynmap.fabric;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.dynmap.DynmapChunk;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Detached chunk data for render workers; live chunk copies run on the server thread. */
public class FabricMapChunkCache extends GenericMapChunkCache {
    private final FabricChunkProvider<SerializableChunkData> provider;
    private FabricWorld world;
    private ServerLevel level;
    private ServerChunkCache chunks;

    public FabricMapChunkCache(DynmapPlugin plugin) {
        this(plugin.sscache, plugin.getFabricServer().chunkProvider());
    }

    FabricMapChunkCache(GenericChunkCache cache, FabricChunkProvider<SerializableChunkData> provider) {
        super(cache);
        this.provider = provider;
    }

    public void setChunks(FabricWorld world, List<DynmapChunk> required) {
        this.world = world;
        Level current = world.getLevel();
        if (!(current instanceof ServerLevel serverLevel)) {
            throw new CancellationException("Fabric world is no longer loaded");
        }
        level = serverLevel;
        chunks = level.getChunkSource();
        super.setChunks(world, required);
    }

    protected boolean isCurrentWorld() {
        return world.getLevel() == level && level.getChunkSource() == chunks;
    }

    void requireCurrentWorld() {
        FabricChunkProvider.requireCurrentWorld(this::isCurrentWorld);
    }

    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
        return provider.request(this::isCurrentWorld, () -> copyLoadedChunk(chunk),
                snapshot -> snapshot == null ? null : parseChunkFromNBT(new NBT.NBTCompound(snapshot.write())));
    }

    protected SerializableChunkData copyLoadedChunk(DynmapChunk chunk) {
        // Resolve on the owning thread and reject an obsolete dimension instance.
        FabricChunkProvider.requireCurrentWorld(() -> level.getServer().getLevel(level.dimension()) == level);
        if (!chunks.hasChunk(chunk.x, chunk.z)) return null;
        LevelChunk loaded = chunks.getChunkNow(chunk.x, chunk.z);
        return loaded == null ? null : SerializableChunkData.copyOf(level, loaded);
    }

    protected CompletableFuture<Optional<CompoundTag>> readStoredChunk(DynmapChunk chunk) {
        return chunks.chunkMap.read(new ChunkPos(chunk.x, chunk.z));
    }

    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk) {
        // Start independent reads before consuming results; never load or generate a live chunk.
        CompletableFuture<Optional<CompoundTag>> result = provider.read(this::isCurrentWorld,
                () -> readStoredChunk(chunk));
        return () -> {
            CompoundTag nbt = provider.await(result).orElse(null);
            requireCurrentWorld();
            GenericChunk parsed = nbt == null ? null : parseChunkFromNBT(new NBT.NBTCompound(nbt));
            requireCurrentWorld();
            return parsed;
        };
    }
}
