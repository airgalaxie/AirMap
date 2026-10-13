package org.dynmap.fabric.event;

import net.minecraft.server.level.ServerPlayer;

public class ServerChatEvents {
    private ServerChatEvents() {
    }

    public static final SimpleEvent<ServerChatCallback> EVENT = new SimpleEvent<>(ServerChatCallback.class,
            (listeners) -> (player, message) -> {
                for (ServerChatCallback callback : listeners) {
                    callback.onChatMessage(player, message);
                }
            }
    );

    @FunctionalInterface
    public interface ServerChatCallback {
        void onChatMessage(ServerPlayer player, String message);
    }
}
