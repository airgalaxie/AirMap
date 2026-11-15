package org.dynmap.fabric.permissions;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
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
        Identifier node;
        try {
            node = Identifier.fromNamespaceAndPath("dynmap", permission);
        } catch (RuntimeException ex) {
            node = null;
        }
        if (node == null) return fallback.has(player, permission);
        if (!(player instanceof PermissionContextOwner context)) return fallback.has(player, permission);
        return context.checkPermission(node)
                .orElseGet(() -> fallback.has(player, permission));
    }

    @Override
    public boolean hasPermissionNode(Player player, String permission) {
        if (player == null) return false;
        if (DynmapPlugin.plugin.isOp(player.getName().getString().toLowerCase())) return true;
        // Full nodes may use Fabric's namespace:path form or the legacy dotted form.
        Identifier node = (permission != null && permission.indexOf(':') >= 0) ? Identifier.tryParse(permission)
                : dottedNode(permission);
        if (node == null) return fallback.hasPermissionNode(player, permission);
        if (!(player instanceof PermissionContextOwner context)) return fallback.hasPermissionNode(player, permission);
        return context.checkPermission(node)
                .orElseGet(() -> fallback.hasPermissionNode(player, permission));
    }

    private static Identifier dottedNode(String permission) {
        int separator = permission == null ? -1 : permission.indexOf('.');
        return separator <= 0 ? null
                : Identifier.tryParse(permission.substring(0, separator) + ":" + permission.substring(separator + 1));
    }
}