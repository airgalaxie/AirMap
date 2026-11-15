package org.dynmap.fabric.permissions;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.dynmap.fabric.DynmapPlugin;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricPermissionsTest {
    private DynmapPlugin previous;
    private ServerPlayer player;
    private PermissionContextOwner context;
    private PermissionProvider fallback;
    private FabricPermissions permissions;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() {
        previous = DynmapPlugin.plugin;
        DynmapPlugin.plugin = mock(DynmapPlugin.class);
        player = mock(ServerPlayer.class, withSettings().extraInterfaces(PermissionContextOwner.class));
        when(player.getName()).thenReturn(Component.literal("Alex"));
        context = (PermissionContextOwner) player;
        fallback = mock(PermissionProvider.class);
        permissions = new FabricPermissions(fallback);
    }

    @AfterEach
    void restore() { DynmapPlugin.plugin = previous; }

    @Test
    void checksFullDottedAndNamespacedNodesWithoutDynmapPrefix() {
        Identifier node = Identifier.fromNamespaceAndPath("group", "vip");
        when(context.checkPermission(node)).thenReturn(TriState.TRUE);
        assertTrue(permissions.hasPermissionNode(player, "group.vip"));
        assertTrue(permissions.hasPermissionNode(player, "group:vip"));
        verify(context, times(2)).checkPermission(node);
        verifyNoInteractions(fallback);
    }

    @Test
    void explicitDenialOverridesFallback() {
        when(context.checkPermission(Identifier.parse("group:vip"))).thenReturn(TriState.FALSE);
        assertFalse(permissions.hasPermissionNode(player, "group.vip"));
        verifyNoInteractions(fallback);
    }

    @Test
    void undefinedAndInvalidNodesUseFullNodeFallback() {
        when(context.checkPermission(Identifier.parse("group:vip"))).thenReturn(TriState.DEFAULT);
        when(fallback.hasPermissionNode(player, "group.vip")).thenReturn(true);
        assertTrue(permissions.hasPermissionNode(player, "group.vip"));
        assertFalse(permissions.hasPermissionNode(player, "Invalid Node"));
        verify(fallback).hasPermissionNode(player, "Invalid Node");
    }

    @Test
    void operatorBypassesProviderAndNullPlayerIsDenied() {
        when(DynmapPlugin.plugin.isOp("alex")).thenReturn(true);
        assertTrue(permissions.hasPermissionNode(player, "group.vip"));
        assertFalse(permissions.hasPermissionNode(null, "group.vip"));
        verify(context, never()).checkPermission(any(Identifier.class));
        verifyNoInteractions(fallback);
    }
}
