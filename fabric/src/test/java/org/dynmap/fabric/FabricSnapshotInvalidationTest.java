package org.dynmap.fabric;

import static org.junit.jupiter.api.Assertions.*;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.junit.jupiter.api.Test;

class FabricSnapshotInvalidationTest {
    @Test void blockInvalidationRemovesOnlyAffectedChunkAndWorldIncludingNegativeCoordinates() {
        DynmapPlugin plugin = new DynmapPlugin();
        plugin.sscache = new GenericChunkCache(32, true);
        GenericChunkCache.ChunkCacheRec old = new GenericChunkCache.ChunkCacheRec();
        old.ss = new GenericChunk.Builder(0, 16).build();
        plugin.sscache.putSnapshot("minecraft:overworld", -2, 1, old);
        plugin.sscache.putSnapshot("minecraft:overworld", -1, 1, old);
        plugin.sscache.putSnapshot("example:moon", -2, 1, old);
        plugin.invalidateSnapshot("minecraft:overworld", -17, 8, 16);
        assertNull(plugin.sscache.getSnapshot("minecraft:overworld", -2, 1));
        assertSame(old, plugin.sscache.getSnapshot("minecraft:overworld", -1, 1));
        assertSame(old, plugin.sscache.getSnapshot("example:moon", -2, 1));
        GenericChunkCache.ChunkCacheRec fresh = new GenericChunkCache.ChunkCacheRec();
        fresh.ss = new GenericChunk.Builder(0, 16).build();
        plugin.sscache.putSnapshot("minecraft:overworld", -2, 1, fresh);
        assertSame(fresh, plugin.sscache.getSnapshot("minecraft:overworld", -2, 1));
    }
}
