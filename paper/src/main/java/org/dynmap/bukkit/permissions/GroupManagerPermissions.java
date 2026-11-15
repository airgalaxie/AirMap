package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;
import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.dataholder.OverloadedWorldHolder;
import org.anjocaido.groupmanager.dataholder.worlds.WorldsHolder;
import org.anjocaido.groupmanager.permissions.AnjoPermissionsHandler;

public class GroupManagerPermissions implements PermissionProvider {
    String name;
    WorldsHolder wh;

    public static GroupManagerPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("GroupManager");
        if (permissionsPlugin == null)
            return null;
        server.getPluginManager().enablePlugin(permissionsPlugin);
        if(permissionsPlugin.isEnabled() == false)
            return null;
        Log.info("Using GroupManager " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new GroupManagerPermissions(name, permissionsPlugin);
    }

    public GroupManagerPermissions(String name, Plugin permissionsPlugin) {
        this(name, ((GroupManager) permissionsPlugin).getWorldsHolder());
    }

    GroupManagerPermissions(String name, WorldsHolder worlds) {
        this.name = name;
        wh = worlds;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) return true;
        AnjoPermissionsHandler handler = wh.getWorldPermissions(player);
        return handler != null && handler.permission(player, name + "." + permission);
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> hasperms = new HashSet<String>();
        // Offline requests have no current world; retain the default-world policy.
        OverloadedWorldHolder defaultWorld = wh == null ? null : wh.getDefaultWorld();
        AnjoPermissionsHandler apm = defaultWorld == null ? null : defaultWorld.getPermissionsHandler();
        if (apm != null) {
            for (String pp : perms) {
                if (apm.permission(player, name + "." + pp)) {
                    hasperms.add(pp);
                }
            }
        }
        return hasperms;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        // Offline requests have no current world; retain the default-world policy.
        OverloadedWorldHolder defaultWorld = wh == null ? null : wh.getDefaultWorld();
        AnjoPermissionsHandler apm = defaultWorld == null ? null : defaultWorld.getPermissionsHandler();
        boolean rslt = false;
        if (apm != null) {
            rslt = apm.permission(player, name + "." + perm);
        }
        return rslt;
    }
}