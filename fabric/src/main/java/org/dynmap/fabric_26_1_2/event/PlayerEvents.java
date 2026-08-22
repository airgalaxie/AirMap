package org.dynmap.fabric_26_1_2.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public class PlayerEvents {
    private static boolean fabricEventsRegistered;

    private PlayerEvents() {
    }

    public static synchronized void registerFabricEvents() {
        if (fabricEventsRegistered) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                PLAYER_LOGGED_IN.invoker().onPlayerLoggedIn(listener.player));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                PLAYER_LOGGED_OUT.invoker().onPlayerLoggedOut(listener.player));

        fabricEventsRegistered = true;
    }

    public static Event<PlayerLoggedIn> PLAYER_LOGGED_IN = EventFactory.createArrayBacked(PlayerLoggedIn.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedIn callback : listeners) {
                    callback.onPlayerLoggedIn(player);
                }
            }
    );

    public static Event<PlayerLoggedOut> PLAYER_LOGGED_OUT = EventFactory.createArrayBacked(PlayerLoggedOut.class,
            (listeners) -> (player) -> {
                for (PlayerLoggedOut callback : listeners) {
                    callback.onPlayerLoggedOut(player);
                }
            }
    );

    public static Event<PlayerChangedDimension> PLAYER_CHANGED_DIMENSION = EventFactory.createArrayBacked(PlayerChangedDimension.class,
            (listeners) -> (player) -> {
                for (PlayerChangedDimension callback : listeners) {
                    callback.onPlayerChangedDimension(player);
                }
            }
    );

    public static Event<PlayerRespawn> PLAYER_RESPAWN = EventFactory.createArrayBacked(PlayerRespawn.class,
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
