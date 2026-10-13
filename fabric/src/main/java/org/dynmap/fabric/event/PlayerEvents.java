package org.dynmap.fabric.event;

import net.minecraft.server.level.ServerPlayer;

public class PlayerEvents {
    private PlayerEvents() {
    }

    public static final SimpleEvent<PlayerLoggedIn> PLAYER_LOGGED_IN = new SimpleEvent<>(PlayerLoggedIn.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedIn callback : listeners) {
                    callback.onPlayerLoggedIn(player);
                }
            }
    );

    public static final SimpleEvent<PlayerLoggedOut> PLAYER_LOGGED_OUT = new SimpleEvent<>(PlayerLoggedOut.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedOut callback : listeners) {
                    callback.onPlayerLoggedOut(player);
                }
            }
    );

    public static final SimpleEvent<PlayerChangedDimension> PLAYER_CHANGED_DIMENSION = new SimpleEvent<>(PlayerChangedDimension.class,
            (listeners) -> (player) -> {
                for (PlayerChangedDimension callback : listeners) {
                    callback.onPlayerChangedDimension(player);
                }
            }
    );

    public static final SimpleEvent<PlayerRespawn> PLAYER_RESPAWN = new SimpleEvent<>(PlayerRespawn.class,
            (listeners) -> (player) -> {
                for (PlayerRespawn callback : listeners) {
                    callback.onPlayerRespawn(player);
                }
            }
    );

    @FunctionalInterface
    public interface PlayerLoggedIn {
        void onPlayerLoggedIn(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerLoggedOut {
        void onPlayerLoggedOut(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerChangedDimension {
        void onPlayerChangedDimension(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerRespawn {
        void onPlayerRespawn(ServerPlayer player);
    }
}
