package org.dynmap.fabric.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.dynmap.Log;
import org.dynmap.fabric.DynmapPlugin;

import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class LuckPermissions implements PermissionProvider {

    private LuckPerms api;
    private final BooleanSupplier serverThread;
    private final ConcurrentHashMap<String, CompletableFuture<User>> loads = new ConcurrentHashMap<>();

    public LuckPermissions(BooleanSupplier serverThread) {
        this.serverThread = serverThread;
    }

    LuckPermissions(LuckPerms api, BooleanSupplier serverThread) {
        this(serverThread);
        this.api = api;
    }

    private Optional<LuckPerms> getApi() {
        if (api != null) return Optional.of(api);
        try {
            api = LuckPermsProvider.get();
            return Optional.of(api);
        } catch (Exception ex) {
            Log.warning("Trying to access LuckPerms before it has loaded");
            return Optional.empty();
        }
    }

    private String permissionKey(String perm) {
        return "dynmap." + perm;
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        if (DynmapPlugin.plugin.isOp(player.toLowerCase(Locale.ROOT))) return Set.copyOf(perms);
        CachedPermissionData data = offlineData(player);
        if (data == null) return Set.of();
        return perms.stream().filter(perm -> data.checkPermission(permissionKey(perm)).asBoolean())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return hasOfflinePermissions(player, Set.of(perm)).contains(perm);
    }

    private CachedPermissionData offlineData(String username) {
        LuckPerms luckPerms = getApi().orElse(null);
        if (luckPerms == null) return null;
        var manager = luckPerms.getUserManager();
        User user = manager.getUser(username);
        if (user == null) {
            String key = username.toLowerCase(Locale.ROOT);
            CompletableFuture<User> load = loads.computeIfAbsent(key, ignored ->
                    manager.lookupUniqueId(username).thenCompose(uuid -> uuid == null
                            ? CompletableFuture.completedFuture(null) : manager.loadUser(uuid)));
            load.whenComplete((value, failure) -> loads.remove(key, load));
            // The synchronous permission contract must never wait for I/O on the server thread.
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
        return user == null ? null : user.getCachedData().getPermissionData();
    }

    @Override
    public boolean has(Player player, String permission) {
        return hasPermissionNode(player, permissionKey(permission));
    }

    @Override
    public boolean hasPermissionNode(Player player, String permission) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        String name = player.getName().getString().toLowerCase();
        if (DynmapPlugin.plugin.isOp(name)) return true;
        return getApi().map(api -> api.getPlayerAdapter(ServerPlayer.class)
                .getPermissionData(serverPlayer).checkPermission(permission).asBoolean())
                .orElse(false);
    }

}
