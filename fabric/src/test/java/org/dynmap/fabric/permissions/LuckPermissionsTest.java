package org.dynmap.fabric.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.util.Tristate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.dynmap.fabric.DynmapPlugin;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckPermissionsTest {
    private DynmapPlugin previous;
    private LuckPerms api;
    private UserManager manager;
    private User user;
    private CachedPermissionData data;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() {
        previous = DynmapPlugin.plugin;
        DynmapPlugin.plugin = mock(DynmapPlugin.class);
        api = mock(LuckPerms.class);
        manager = mock(UserManager.class);
        user = mock(User.class);
        CachedDataManager cache = mock(CachedDataManager.class);
        data = mock(CachedPermissionData.class);
        when(api.getUserManager()).thenReturn(manager);
        when(user.getCachedData()).thenReturn(cache);
        when(cache.getPermissionData()).thenReturn(data);
        when(data.checkPermission(anyString())).thenReturn(Tristate.FALSE);
        when(data.checkPermission("dynmap.stats")).thenReturn(Tristate.TRUE);
    }

    @AfterEach
    void restore() { DynmapPlugin.plugin = previous; }

    @Test
    @SuppressWarnings("unchecked")
    void fullNodeIsPassedUnchangedAndShortPermissionGetsPrefix() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getName()).thenReturn(Component.literal("Alex"));
        PlayerAdapter<ServerPlayer> adapter = mock(PlayerAdapter.class);
        when(api.getPlayerAdapter(ServerPlayer.class)).thenReturn(adapter);
        when(adapter.getPermissionData(player)).thenReturn(data);
        when(data.checkPermission("group.vip")).thenReturn(Tristate.TRUE);
        LuckPermissions permissions = new LuckPermissions(api, () -> true);
        assertTrue(permissions.hasPermissionNode(player, "group.vip"));
        assertTrue(permissions.has(player, "stats"));
        assertFalse(permissions.hasPermissionNode(player, "group.denied"));
        assertFalse(permissions.hasPermissionNode(null, "group.vip"));
        verify(data).checkPermission("group.vip");
        verify(data, never()).checkPermission("dynmap.group.vip");
    }

    @Test
    void batchResolvesUuidAndLoadsUserOnlyOnce() {
        UUID uuid = UUID.randomUUID();
        when(manager.lookupUniqueId("Alex")).thenReturn(CompletableFuture.completedFuture(uuid));
        when(manager.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(user));
        LuckPermissions permissions = new LuckPermissions(api, () -> false);
        assertEquals(Set.of("stats"), permissions.hasOfflinePermissions("Alex", Set.of("stats", "render")));
        verify(manager).lookupUniqueId("Alex");
        verify(manager).loadUser(uuid);
        verify(user).getCachedData();
    }

    @Test
    void serverThreadNeverWaitsAndCoalescesPendingRequests() {
        CompletableFuture<UUID> lookup = new CompletableFuture<>();
        when(manager.lookupUniqueId("Alex")).thenReturn(lookup);
        LuckPermissions permissions = new LuckPermissions(api, () -> true);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertFalse(permissions.hasOfflinePermission("Alex", "stats"));
            assertFalse(permissions.hasOfflinePermission("Alex", "stats"));
        });
        verify(manager).lookupUniqueId("Alex");
        when(manager.getUser("Alex")).thenReturn(user);
        assertTrue(permissions.hasOfflinePermission("Alex", "stats"));
    }

    @Test
    void workerWaitsForAsyncLookupAndLoad() throws Exception {
        UUID uuid = UUID.randomUUID();
        CompletableFuture<User> load = new CompletableFuture<>();
        when(manager.lookupUniqueId("Alex")).thenReturn(CompletableFuture.completedFuture(uuid));
        var requested = new java.util.concurrent.CountDownLatch(1);
        when(manager.loadUser(uuid)).thenAnswer(invocation -> {
            requested.countDown();
            return load;
        });
        var result = CompletableFuture.supplyAsync(() ->
                new LuckPermissions(api, () -> false).hasOfflinePermission("Alex", "stats"));
        assertTrue(requested.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(result.isDone());
        load.complete(user);
        assertTrue(result.get(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void interruptedWorkerRestoresInterruptAndDenies() {
        when(manager.lookupUniqueId("Alex")).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertFalse(new LuckPermissions(api, () -> false).hasOfflinePermission("Alex", "stats"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
    @Test
    void cachedUserNeedsNoIoEvenOnServerThread() {
        when(manager.getUser("Alex")).thenReturn(user);
        assertTrue(new LuckPermissions(api, () -> true).hasOfflinePermission("Alex", "stats"));
        verify(manager, never()).lookupUniqueId(anyString());
        verify(manager, never()).loadUser(any(UUID.class));
    }

    @Test
    void failedAndUnknownLookupsDeny() {
        var permissions = new LuckPermissions(api, () -> false);
        when(manager.lookupUniqueId("Unknown")).thenReturn(CompletableFuture.completedFuture(null));
        assertFalse(permissions.hasOfflinePermission("Unknown", "stats"));
        when(manager.lookupUniqueId("Alex")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertFalse(permissions.hasOfflinePermission("Alex", "stats"));
    }

}
