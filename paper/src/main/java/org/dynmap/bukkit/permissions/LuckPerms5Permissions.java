package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ExecutionException;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.cacheddata.CachedDataManager;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.dynmap.Log;

public class LuckPerms5Permissions implements PermissionProvider {
    String name;
    LuckPerms luckPerms;
    private final BooleanSupplier serverThread;
    private final ConcurrentHashMap<String, CompletableFuture<User>> loads = new ConcurrentHashMap<>();

    public static LuckPerms5Permissions create(Server server, String name) {
        try {
            Class.forName("net.luckperms.api.LuckPerms");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        if (!server.getPluginManager().isPluginEnabled("LuckPerms"))
            return null;
        LuckPerms luckPerms = server.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null)
            return null;
        Log.info("Using LuckPerms " + luckPerms.getPluginMetadata().getVersion() + " for access control");
        return new LuckPerms5Permissions(name, luckPerms);
    }

    public LuckPerms5Permissions(String name, LuckPerms luckPerms) {
        this(name, luckPerms, Bukkit::isPrimaryThread);
    }

    LuckPerms5Permissions(String name, LuckPerms luckPerms, BooleanSupplier serverThread) {
        this.name = name;
        this.luckPerms = luckPerms;
        this.serverThread = serverThread;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(name + "." + permission);
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        Set<String> result = new HashSet<>();
        CachedPermissionData user = getUser(player);
        if (user != null) {
            for (String p : perms) {
                if (user.checkPermission(name + "." + p).asBoolean())
                    result.add(p);
            }
        }
        return result;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        CachedPermissionData user = getUser(player);
        if (user == null)
            return false;
        return user.checkPermission(name + "." + perm).asBoolean();
    }

    private CachedPermissionData getUser(String username) {
        var manager = luckPerms.getUserManager();
        User user = manager.getUser(username);
        if (user == null) {
            String key = username.toLowerCase(Locale.ROOT);
            CompletableFuture<User> load = loads.computeIfAbsent(key, ignored ->
                    manager.lookupUniqueId(username).thenCompose(uuid -> uuid == null
                            ? CompletableFuture.completedFuture(null) : manager.loadUser(uuid)));
            load.whenComplete((value, failure) -> loads.remove(key, load));
            // An uncached result denies this check while loading asynchronously.
            if (serverThread.getAsBoolean() && !load.isDone()) return null;
            try {
                user = load.get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException | java.util.concurrent.CancellationException failure) {
                Log.warning("Unable to load LuckPerms permissions for " + username);
                return null;
            }
        }

        if (user == null)
            return null;

        CachedDataManager data = user.getCachedData();
        return luckPerms
            .getContextManager()
            .getQueryOptions(user)
            .map(queryOptions -> data.getPermissionData(queryOptions))
            .orElse(null);
    }

}
