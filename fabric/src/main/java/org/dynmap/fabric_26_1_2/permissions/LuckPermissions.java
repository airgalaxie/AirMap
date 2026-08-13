package org.dynmap.fabric_26_1_2.permissions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import org.dynmap.Log;
import org.dynmap.fabric_26_1_2.DynmapPlugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LuckPermissions implements PermissionProvider {

    private LuckPerms api = null;

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

    private Optional<UUID> cachedUUID(String username) {
        try (BufferedReader reader = new BufferedReader(new FileReader("usercache.json"))) {
            JsonArray cache = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement entry : cache) {
                JsonObject user = entry.getAsJsonObject();
                if (user.get("name").getAsString().equalsIgnoreCase(username)) {
                    String uuid = user.get("uuid").getAsString();
                    return Optional.of(UUID.fromString(uuid));
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException ex) {
            Log.warning("Unable to read usercache.json");
        }

        return Optional.empty();
    }

    private String permissionKey(String perm) {
        return "dynmap." + perm;
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        return perms.stream()
                .filter(perm -> hasOfflinePermission(player, perm))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        if (DynmapPlugin.plugin.isOp(player.toLowerCase())) return true;
        Optional<LuckPerms> api = getApi();
        Optional<UUID> uuid = cachedUUID(player);
        if (!uuid.isPresent() || !api.isPresent()) return false;
        User user = api.get().getUserManager().loadUser(uuid.get()).join();
        CachedPermissionData permissions = user.getCachedData().getPermissionData();
        Tristate state = permissions.checkPermission(permissionKey(perm));
        return state.asBoolean();
    }

    @Override
    public boolean has(Player player, String permission) {
        if (player == null) return false;
        String name = player.getName().getString().toLowerCase();
        if (DynmapPlugin.plugin.isOp(name)) return true;
        return Permissions.check(player, permissionKey(permission));
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
