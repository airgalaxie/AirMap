package org.dynmap.fabric.mixin;

import net.minecraft.server.MinecraftServer;

import org.dynmap.fabric.MinecraftAdapterHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(
            method = "runServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z")
    )
    private void airmap$beforeServerInitialization(CallbackInfo info) {
        MinecraftAdapterHooks.serverStarting((MinecraftServer) (Object) this);
    }

    @Inject(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;"
            )
    )
    private void airmap$afterServerInitialization(CallbackInfo info) {
        MinecraftAdapterHooks.serverStarted((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void airmap$afterServerTick(BooleanSupplier shouldKeepTicking, CallbackInfo info) {
        MinecraftAdapterHooks.endServerTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void airmap$beforeServerStop(CallbackInfo info) {
        MinecraftAdapterHooks.serverStopping((MinecraftServer) (Object) this);
    }
}
