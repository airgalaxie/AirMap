package org.dynmap.fabric_26_1_2.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;

import org.dynmap.fabric_26_1_2.event.PlayerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
//    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerLevel;DDDFF)V", at = @At("RETURN"))
//    public void teleport(ServerLevel targetLevel, double x, double y, double z, float yaw, float pitch, CallbackInfo info) {
//        ServerPlayer player = (ServerPlayer) (Object) this;
//        if (targetLevel != player.getServerLevel()) {
//            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
//        }
//    }

    @Inject(method = "teleport", at = @At("TAIL"))
    public void teleport(TeleportTransition teleportTransition, CallbackInfoReturnable<ServerPlayer> info) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.getRemovalReason() == null) {
            PlayerEvents.PLAYER_CHANGED_DIMENSION.invoker().onPlayerChangedDimension(player);
        }
    }
}
