package org.dynmap.fabric.mixin;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.dynmap.fabric.MinecraftAdapterHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void airmap$registerCommands(
            Commands.CommandSelection selection,
            CommandBuildContext context,
            CallbackInfo info
    ) {
        MinecraftAdapterHooks.registerCommands(((Commands) (Object) this).getDispatcher());
    }
}
