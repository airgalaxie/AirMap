package org.dynmap.fabric;

import net.minecraft.world.entity.player.Player;
import org.dynmap.fabric.permissions.PermissionProvider;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynmapPluginPermissionsTest {
    @Test
    void delegatesOfflineBatchToProviderOnce() throws Exception {
        DynmapPlugin previous = DynmapPlugin.plugin;
        try {
            DynmapPlugin plugin = new DynmapPlugin();
            AtomicInteger calls = new AtomicInteger();
            PermissionProvider provider = new PermissionProvider() {
                public boolean has(Player player, String permission) { return false; }
                public boolean hasPermissionNode(Player player, String permission) { return false; }
                public boolean hasOfflinePermission(String player, String permission) { return false; }
                public Set<String> hasOfflinePermissions(String player, Set<String> permissions) {
                    calls.incrementAndGet();
                    assertEquals("Alex", player);
                    assertEquals(Set.of("render", "stats"), permissions);
                    return Set.of("stats");
                }
            };
            var field = DynmapPlugin.class.getDeclaredField("permissions");
            field.setAccessible(true);
            field.set(plugin, provider);
            assertEquals(Set.of("stats"), plugin.hasOfflinePermissions("Alex", Set.of("render", "stats")));
            assertEquals(1, calls.get());
        } finally {
            DynmapPlugin.plugin = previous;
        }
    }
}
