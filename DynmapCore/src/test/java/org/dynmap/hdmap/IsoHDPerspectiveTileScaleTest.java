package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.utils.TileFlags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsoHDPerspectiveTileScaleTest {
    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    void rangeLookupContainsIndividualTilesAtPositiveAndNegativeCoordinates(int tilescale) {
        IsoHDPerspective perspective = buildPerspective();

        assertRangeContainsIndividualTiles(perspective, 700, 40, 900, 760, 80, 960, tilescale);
        assertRangeContainsIndividualTiles(perspective, -760, 40, -960, -700, 80, -900, tilescale);
    }

    private static void assertRangeContainsIndividualTiles(IsoHDPerspective perspective,
            int minx, int miny, int minz, int maxx, int maxy, int maxz, int tilescale) {
        Set<TileFlags.TileCoord> rangeTiles = new HashSet<>(perspective.getTileCoords(
                null, minx, miny, minz, maxx, maxy, maxz, tilescale));

        int[][] corners = {
                { minx, miny, minz },
                { minx, miny, maxz },
                { minx, maxy, minz },
                { minx, maxy, maxz },
                { maxx, miny, minz },
                { maxx, miny, maxz },
                { maxx, maxy, minz },
                { maxx, maxy, maxz }
        };
        for (int[] corner : corners) {
            List<TileFlags.TileCoord> individualTiles = perspective.getTileCoords(
                    null, corner[0], corner[1], corner[2], tilescale);
            assertTrue(rangeTiles.containsAll(individualTiles),
                    "range lookup omitted an endpoint tile at tilescale=" + tilescale);
        }
    }

    private static IsoHDPerspective buildPerspective() {
        ConfigurationNode cfg = new ConfigurationNode();
        cfg.put("name", "tile-scale-test");
        cfg.put("azimuth", 135.0);
        cfg.put("inclination", 30.0);
        cfg.put("scale", 16);
        return new IsoHDPerspective(new DynmapCore(), cfg);
    }
}
