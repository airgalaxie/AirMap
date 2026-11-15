package org.dynmap.bukkit.permissions;

import org.anjocaido.groupmanager.dataholder.OverloadedWorldHolder;
import org.anjocaido.groupmanager.dataholder.worlds.WorldsHolder;
import org.anjocaido.groupmanager.permissions.AnjoPermissionsHandler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupManagerPermissionsTest {
    @Test
    void onlinePermissionsFollowPlayersCurrentWorld() {
        WorldsHolder worlds = mock(WorldsHolder.class);
        Player player = mock(Player.class);
        AnjoPermissionsHandler overworld = mock(AnjoPermissionsHandler.class);
        AnjoPermissionsHandler nether = mock(AnjoPermissionsHandler.class);
        when(overworld.permission(player, "dynmap.render")).thenReturn(true);
        when(worlds.getWorldPermissions(player)).thenReturn(overworld, nether);
        var permissions = new GroupManagerPermissions("dynmap", worlds);
        assertTrue(permissions.has(player, "render"));
        assertFalse(permissions.has(player, "render"));
        verify(worlds, never()).getDefaultWorld();
        verify(nether).permission(player, "dynmap.render");
    }

    @Test
    void missingWorldHandlerDeniesAndConsoleRemainsAllowed() {
        var permissions = new GroupManagerPermissions("dynmap", mock(WorldsHolder.class));
        assertFalse(permissions.has(mock(Player.class), "render"));
        assertTrue(permissions.has(mock(CommandSender.class), "render"));
    }

    @Test
    void offlinePermissionsKeepExplicitDefaultWorldPolicy() {
        WorldsHolder worlds = mock(WorldsHolder.class);
        OverloadedWorldHolder world = mock(OverloadedWorldHolder.class);
        AnjoPermissionsHandler handler = mock(AnjoPermissionsHandler.class);
        when(worlds.getDefaultWorld()).thenReturn(world);
        when(world.getPermissionsHandler()).thenReturn(handler);
        when(handler.permission("Alex", "dynmap.stats")).thenReturn(true);
        var permissions = new GroupManagerPermissions("dynmap", worlds);
        assertEquals(Set.of("stats"), permissions.hasOfflinePermissions("Alex", Set.of("stats", "render")));
        assertTrue(permissions.hasOfflinePermission("Alex", "stats"));
        verify(worlds, never()).getWorldPermissions(any(Player.class));
    }
}
