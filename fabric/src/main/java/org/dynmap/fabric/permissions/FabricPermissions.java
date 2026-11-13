package org.dynmap.fabric.permissions;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.dynmap.Log;
import org.dynmap.fabric.DynmapPlugin;

import java.util.Set;
import java.util.stream.Collectors;

public class FabricPermissions implements PermissionProvider {

    private final PermissionProvider fallback;

    public FabricPermissions(PermissionProvider fallback) {
        this.fallback = fallback;
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        return perms.stream()
                .filter(perm -> hasOfflinePermission(player, perm))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return fallback.hasOfflinePermission(player, perm);
    }

    @Override
    public boolean has(Player player, String permission) {
        if (player == null) return false;
        String name = player.getName().getString().toLowerCase();
        if (DynmapPlugin.plugin.isOp(name)) return true;
        return ((PermissionContextOwner) player)
                .checkPermission(Identifier.fromNamespaceAndPath("dynmap", permission))
                .orElseGet(() -> fallback.has(player, permission));
    }

    @Override
    public boolean hasPermissionNode(Player player, String permission) {
        if (player != null) {
            String name = player.getName().getString().toLowerCase();
            return DynmapPlugin.plugin.isOp(name);
        }
        return false;
    }

}
