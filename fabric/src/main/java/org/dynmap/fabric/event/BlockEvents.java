package org.dynmap.fabric.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class BlockEvents {
    private BlockEvents() {
    }

    public static final SimpleEvent<BlockCallback> BLOCK_EVENT = new SimpleEvent<>(BlockCallback.class,
            (listeners) -> (world, pos) -> {
                for (BlockCallback callback : listeners) {
                    callback.onBlockEvent(world, pos);
                }
            }
    );

    public static final SimpleEvent<SignChangeCallback> SIGN_CHANGE_EVENT = new SimpleEvent<>(SignChangeCallback.class,
            (listeners) -> (world, pos, lines, player, front) -> {
                for (SignChangeCallback callback : listeners) {
                    callback.onSignChange(world, pos, lines, player, front);
                }
            }
    );

    @FunctionalInterface
    public interface BlockCallback {
        void onBlockEvent(Level world, BlockPos pos);
    }

    @FunctionalInterface
    public interface SignChangeCallback {
        void onSignChange(ServerLevel world, BlockPos pos, String[] lines, ServerPlayer player, boolean front);
    }
}
