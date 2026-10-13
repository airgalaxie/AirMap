package org.dynmap.fabric.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public class CustomServerChunkEvents {
    public static final SimpleEvent<ChunkGenerate> CHUNK_GENERATE = new SimpleEvent<>(ChunkGenerate.class,
            (listeners) -> (world, chunk) -> {
                for (ChunkGenerate callback : listeners) {
                    callback.onChunkGenerate(world, chunk);
                }
            }
    );

    @FunctionalInterface
    public interface ChunkGenerate {
        void onChunkGenerate(ServerLevel world, ChunkAccess chunk);
    }
}
