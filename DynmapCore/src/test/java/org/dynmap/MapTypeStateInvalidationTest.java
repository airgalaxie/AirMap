package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.TileFlags;
import org.junit.jupiter.api.Test;

class MapTypeStateInvalidationTest {
    private static final DynmapWorld WORLD = new DynmapWorld("test:coordination", 256, 63) {
        @Override public boolean isNether() { return false; }
        @Override public DynmapLocation getSpawnLocation() { return new DynmapLocation(getName(), 0, 64, 0); }
        @Override public long getTime() { return 0; }
        @Override public boolean hasStorm() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isLoaded() { return true; }
        @Override public void setWorldUnloaded() { }
        @Override public int getLightLevel(int x, int y, int z) { return 15; }
        @Override public int getHighestBlockYAt(int x, int z) { return 63; }
        @Override public boolean canGetSkyLightLevel() { return false; }
        @Override public int getSkyLightLevel(int x, int y, int z) { return 15; }
        @Override public String getEnvironment() { return "normal"; }
        @Override public MapChunkCache getChunkCache(List<DynmapChunk> chunks) { return null; }
    };

    private static final MapType MAP = new MapType() {
        @Override public void addMapTiles(List<MapTile> list, DynmapWorld w, int tx, int ty) { }
        @Override public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z) { return Collections.emptyList(); }
        @Override public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int minx, int miny, int minz, int maxx, int maxy, int maxz) { return Collections.emptyList(); }
        @Override public MapTile[] getAdjecentTiles(MapTile tile) { return new MapTile[0]; }
        @Override public List<DynmapChunk> getRequiredChunks(MapTile tile) { return Collections.emptyList(); }
        @Override public int getTileSize() { return 128; }
        @Override public String getName() { return "test"; }
        @Override public List<MapType> getMapsSharingRender(DynmapWorld w) { return Collections.singletonList(this); }
        @Override public List<String> getMapNamesSharingRender(DynmapWorld w) { return Collections.singletonList(getName()); }
        @Override public String getPrefix() { return "test"; }
    };

    @Test
    void consumingActiveInvalidationDoesNotClearNewPendingInvalidation() {
        MapTypeState state = new MapTypeState(WORLD, MAP);
        TileFlags.TileCoord coord = new TileFlags.TileCoord();

        assertTrue(state.invalidateTile(7, -3));
        state.save(); // Promote pending invalidations to the active set
        assertTrue(state.popNextInvalidTileCoord(coord));
        assertEquals(7, coord.x);
        assertEquals(-3, coord.y);

        assertTrue(state.invalidateTile(7, -3));
        assertFalse(state.popNextInvalidTileCoord(coord));
        state.save();
        assertTrue(state.popNextInvalidTileCoord(coord));
        assertEquals(7, coord.x);
        assertEquals(-3, coord.y);
        assertFalse(state.popNextInvalidTileCoord(coord));
    }
}
