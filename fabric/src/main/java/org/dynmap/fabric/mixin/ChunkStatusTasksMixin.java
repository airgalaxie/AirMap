package org.dynmap.fabric.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.dynmap.fabric.event.CustomServerChunkEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    @Inject(method = "lambda$full$0", at = @At("TAIL"))
    private static void airmap$afterFullChunkCreated(
            ChunkAccess source,
            WorldGenContext context,
            GenerationChunkHolder holder,
            CallbackInfoReturnable<ChunkAccess> info
    ) {
        if (!(source instanceof ImposterProtoChunk) && info.getReturnValue() instanceof LevelChunk generated) {
            CustomServerChunkEvents.CHUNK_GENERATE.invoker().onChunkGenerate(context.level(), generated);
        }
    }
}
