package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.utils.Matrix3D;
import org.dynmap.utils.TileFlags;
import org.dynmap.utils.Vector3D;
import org.junit.jupiter.api.Test;

class FlatLightingTileBoundaryTest {
    private static IsoHDPerspective perspective(int azimuth, int scale) {
        ConfigurationNode config = new ConfigurationNode();
        config.put("name", "flat-boundary");
        config.put("inclination", 90);
        config.put("azimuth", azimuth);
        config.put("scale", scale);
        return new IsoHDPerspective(null, config);
    }

    private static Matrix3D matrix(IsoHDPerspective perspective, String name) throws Exception {
        Field field = IsoHDPerspective.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Matrix3D) field.get(perspective);
    }

    @Test
    void localUpdatesIncludeEveryAffectedNeighborTileAtEdgesAndCorners() throws Exception {
        for (int azimuth : new int[] {0, 15, 30, 45, 60, 90, 135, 180, 225}) {
            for (int scale : new int[] {1, 4, 16}) {
                IsoHDPerspective perspective = perspective(azimuth, scale);
                Matrix3D transform = matrix(perspective, "world_to_map");
                for (int tileScale : new int[] {0, 1, 2}) {
                    int size = 128 << tileScale;
                    for (int x : new int[] {-129, -33, -32, -1, 0, 31, 32, 127}) {
                        for (int z : new int[] {-129, -33, -32, -1, 0, 31, 32, 127}) {
                            Set<TileFlags.TileCoord> updated = new HashSet<>(
                                    perspective.getTileCoords(null, x, 64, z, tileScale));
                            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                                Vector3D sample = new Vector3D(x + dx + 0.5, 64, z + dz + 0.5);
                                transform.transform(sample);
                                TileFlags.TileCoord expected = new TileFlags.TileCoord(
                                        (int) Math.floor(sample.x / size), (int) Math.floor(sample.y / size));
                                assertTrue(updated.contains(expected), "Flat update omitted a dependent tile: azimuth="
                                        + azimuth + ", scale=" + scale + ", tileScale=" + tileScale + ", x=" + x + ", z=" + z);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void interiorUpdatesStayLocalAndCornerUpdatesIncludeFourTiles() {
        IsoHDPerspective perspective = perspective(180, 4);
        assertEquals(1, perspective.getTileCoords(null, 16, 64, 16, 0).size());
        assertEquals(4, perspective.getTileCoords(null, 31, 64, 31, 0).size());
    }

    @Test
    void requiredChunksContainTheExistingOneBlockLightingHalo() throws Exception {
        // WORLD also supplies the real world height/minY to the production tile bounds.
        Field worldField = FlatLightingTest.class.getDeclaredField("WORLD");
        worldField.setAccessible(true);
        org.dynmap.DynmapWorld world = (org.dynmap.DynmapWorld) worldField.get(null);
        for (int azimuth : new int[] {0, 15, 30, 45, 60, 90, 135, 180, 225}) for (int scale : new int[] {1, 4, 16}) {
            IsoHDPerspective perspective = perspective(azimuth, scale);
            Matrix3D transform = matrix(perspective, "map_to_world");
            for (int tileScale : new int[] {0, 1, 2}) for (int tileX : new int[] {-9, -3, -1, 0, 1, 3, 9}) {
                int size = 128 << tileScale;
                HDMapTile tile = new HDMapTile(world, perspective, tileX, -tileX, 0, tileScale);
                Set<DynmapChunk> chunks = new HashSet<>(perspective.getRequiredChunks(tile));
                for (double x : new double[] {0.5, size - 0.5}) for (double z : new double[] {0.5, size - 0.5}) {
                    Vector3D point = new Vector3D(tileX * size + x, -tileX * size + z, 64);
                    transform.transform(point);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        DynmapChunk sample = new DynmapChunk(((int) Math.floor(point.x) + dx) >> 4,
                                ((int) Math.floor(point.z) + dz) >> 4);
                        assertTrue(chunks.contains(sample), "Tile cache omitted a lighting neighbor");
                    }
                }
            }
        }
    }
}
