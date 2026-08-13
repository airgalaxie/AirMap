package org.dynmap.fabric_26_1_2.mixin;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import org.dynmap.fabric_26_1_2.event.BlockEvents;
import org.dynmap.fabric_26_1_2.event.ServerChatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleDecoratedMessage",
            at = @At(
                    value = "HEAD"
            )
    )
    public void onGameMessage(PlayerChatMessage signedMessage, CallbackInfo ci) {
        ServerChatEvents.EVENT.invoker().onChatMessage(player, signedMessage.signedContent());
    }

    @Inject(
            method = "handleSignUpdate",
            at = @At("HEAD")
    )
    public void onSignUpdate(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        ServerLevel serverLevel = player.level();
        BlockPos blockPos = packet.getPos();
        String[] rawTexts = packet.getLines();
        BlockEvents.SIGN_CHANGE_EVENT.invoker().onSignChange(serverLevel, blockPos, rawTexts, player, packet.isFrontText());
    }
}
