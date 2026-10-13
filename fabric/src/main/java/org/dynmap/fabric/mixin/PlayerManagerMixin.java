package org.dynmap.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.network.Connection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;

import org.dynmap.fabric.event.PlayerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public class PlayerManagerMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void airmap$afterPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo info) {
        PlayerEvents.PLAYER_LOGGED_IN.invoker().onPlayerLoggedIn(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void airmap$beforePlayerRemove(ServerPlayer player, CallbackInfo info) {
        PlayerEvents.PLAYER_LOGGED_OUT.invoker().onPlayerLoggedOut(player);
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void airmap$afterPlayerRespawn(ServerPlayer player, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayer> info) {
        PlayerEvents.PLAYER_RESPAWN.invoker().onPlayerRespawn(info.getReturnValue());
    }
}
