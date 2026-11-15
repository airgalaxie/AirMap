package org.dynmap.bukkit.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckPerms5PermissionsTest {
    private LuckPerms api;
    private UserManager manager;
    private User user;
    private CachedPermissionData data;

    @BeforeEach
    void setup() {
        api = mock(LuckPerms.class);
        manager = mock(UserManager.class);
        user = mock(User.class);
        CachedDataManager cache = mock(CachedDataManager.class);
        ContextManager context = mock(ContextManager.class);
        QueryOptions options = mock(QueryOptions.class);
        data = mock(CachedPermissionData.class);
        when(api.getUserManager()).thenReturn(manager);
        when(api.getContextManager()).thenReturn(context);
        when(context.getQueryOptions(user)).thenReturn(Optional.of(options));
        when(user.getCachedData()).thenReturn(cache);
        when(cache.getPermissionData(options)).thenReturn(data);
        when(data.checkPermission(anyString())).thenReturn(Tristate.FALSE);
        when(data.checkPermission("dynmap.stats")).thenReturn(Tristate.TRUE);
    }

    @Test
    void batchResolvesUuidAndUserOnlyOnce() {
        UUID uuid = UUID.randomUUID();
        when(manager.lookupUniqueId("Alex")).thenReturn(CompletableFuture.completedFuture(uuid));
        when(manager.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(user));
        var permissions = new LuckPerms5Permissions("dynmap", api, () -> false);
        assertEquals(Set.of("stats"), permissions.hasOfflinePermissions("Alex", Set.of("stats", "render")));
        verify(manager).lookupUniqueId("Alex");
        verify(manager).loadUser(uuid);
        verify(user).getCachedData();
    }

    @Test
    void cachedUserNeedsNoIoEvenOnServerThread() {
        when(manager.getUser("Alex")).thenReturn(user);
        assertTrue(new LuckPerms5Permissions("dynmap", api, () -> true).hasOfflinePermission("Alex", "stats"));
        verify(manager, never()).lookupUniqueId(anyString());
        verify(manager, never()).loadUser(any(UUID.class));
    }

    @Test
    void serverThreadNeverWaitsAndCoalescesPendingRequests() {
        CompletableFuture<UUID> lookup = new CompletableFuture<>();
        when(manager.lookupUniqueId("Alex")).thenReturn(lookup);
        var permissions = new LuckPerms5Permissions("dynmap", api, () -> true);
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
        var result = CompletableFuture.supplyAsync(() -> new LuckPerms5Permissions("dynmap", api, () -> false)
                .hasOfflinePermission("Alex", "stats"));
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
            assertFalse(new LuckPerms5Permissions("dynmap", api, () -> false).hasOfflinePermission("Alex", "stats"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void failedAndUnknownLookupsDeny() {
        var permissions = new LuckPerms5Permissions("dynmap", api, () -> false);
        when(manager.lookupUniqueId("Unknown")).thenReturn(CompletableFuture.completedFuture(null));
        assertFalse(permissions.hasOfflinePermission("Unknown", "stats"));
        when(manager.lookupUniqueId("Alex")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertFalse(permissions.hasOfflinePermission("Alex", "stats"));
    }
}
