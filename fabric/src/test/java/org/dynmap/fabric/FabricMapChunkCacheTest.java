package org.dynmap.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.dynmap.DynmapChunk;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.junit.jupiter.api.Test;

class FabricMapChunkCacheTest {
    private final Thread owner = Thread.currentThread();
    private FabricChunkProvider<SerializableChunkData> provider() {
        return new FabricChunkProvider<>(() -> Thread.currentThread() == owner, System::nanoTime);
    }

    private final class Cache extends FabricMapChunkCache {
        boolean current = true;
        RuntimeException copyFailure;
        RuntimeException parseFailure;
        CompletableFuture<Optional<CompoundTag>> read = new CompletableFuture<>();
        int parses;
        Cache(FabricChunkProvider<SerializableChunkData> provider) { super(new GenericChunkCache(16, true), provider); }
        @Override protected boolean isCurrentWorld() { return current; }
        @Override protected SerializableChunkData copyLoadedChunk(DynmapChunk chunk) {
            assertSame(owner, Thread.currentThread());
            if (copyFailure != null) throw copyFailure;
            return null;
        }
        @Override protected CompletableFuture<Optional<CompoundTag>> readStoredChunk(DynmapChunk chunk) { return read; }
        @Override public GenericChunk parseChunkFromNBT(GenericNBTCompound nbt) {
            assertNotSame(owner, Thread.currentThread());
            parses++;
            if (parseFailure != null) throw parseFailure;
            return new GenericChunk.Builder(0, 16).build();
        }
        Supplier<GenericChunk> loaded() { return getLoadedChunkAsync(new DynmapChunk(1, 2)); }
        Supplier<GenericChunk> stored() { return loadChunkAsync(new DynmapChunk(1, 2)); }
    }

    @Test void adapterPropagatesSnapshotCopyFailure() throws Exception {
        try (var provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Cache cache = new Cache(provider); cache.copyFailure = new IllegalStateException("copy failed");
            Supplier<GenericChunk> result = worker.submit(cache::loaded).get();
            provider.processTick(1, Long.MAX_VALUE);
            worker.submit(() -> assertSame(cache.copyFailure, assertThrows(IllegalStateException.class, result::get))).get();
        }
    }

    @Test void adapterPropagatesStoredReadAndParseFailuresInsteadOfReturningNull() throws Exception {
        try (var provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Cache cache = new Cache(provider);
            IOException failure = new IOException("bad region file");
            cache.read.completeExceptionally(failure);
            Supplier<GenericChunk> failedRead = worker.submit(cache::stored).get();
            worker.submit(() -> assertSame(failure, assertThrows(CompletionException.class, failedRead::get).getCause())).get();
            cache.read = CompletableFuture.completedFuture(Optional.of(new CompoundTag()));
            cache.parseFailure = new IllegalArgumentException("bad chunk NBT");
            Supplier<GenericChunk> failedParse = worker.submit(cache::stored).get();
            worker.submit(() -> assertSame(cache.parseFailure, assertThrows(IllegalArgumentException.class, failedParse::get))).get();
            assertEquals(1, cache.parses);
        }
    }

    @Test void missingChunkRemainsNullAndValidChunkIsParsedOnWorker() throws Exception {
        try (var provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Cache cache = new Cache(provider);
            cache.read.complete(Optional.empty());
            Supplier<GenericChunk> missing = worker.submit(cache::stored).get();
            assertNull(worker.submit(missing::get).get()); assertEquals(0, cache.parses);
            cache.read = CompletableFuture.completedFuture(Optional.of(new CompoundTag()));
            Supplier<GenericChunk> present = worker.submit(cache::stored).get();
            assertNotNull(worker.submit(present::get).get()); assertEquals(1, cache.parses);
        }
    }

    @Test void oldWorldCannotPublishDelayedIoAndShutdownReleasesAdapterConsumer() throws Exception {
        try (var provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Cache cache = new Cache(provider);
            Supplier<GenericChunk> result = worker.submit(cache::stored).get();
            cache.current = false; cache.read.complete(Optional.of(new CompoundTag()));
            worker.submit(() -> assertThrows(CancellationException.class, result::get)).get();
            assertEquals(0, cache.parses);
            cache.current = true; cache.read = new CompletableFuture<>();
            Supplier<GenericChunk> pending = worker.submit(cache::stored).get();
            Future<?> waiting = worker.submit(() -> assertThrows(CancellationException.class, pending::get));
            provider.close(); waiting.get(3, TimeUnit.SECONDS);
            assertFalse(cache.read.isCancelled());
        }
    }
}
