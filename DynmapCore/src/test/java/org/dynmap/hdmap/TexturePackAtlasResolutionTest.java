package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TexturePackAtlasResolutionTest {
    @Test
    void resamplingKeepsUvAddressedAtlasAtItsOwnResolution() throws Exception {
        HDBlockStateTextureMap.initializeTable();
        TexturePack pack = allocate(TexturePack.class);
        int[] blockTile = new int[16 * 16];
        int[] atlas = new int[64 * 64];
        for (int i = 0; i < atlas.length; i++) atlas[i] = i;

        set(pack, "native_scale", 16);
        set(pack, "blank", blockTile);
        int[][] tiles = new int[512][];
        tiles[0] = blockTile;
        tiles[1] = atlas;
        set(pack, "tile_argb", tiles);
        set(pack, "nativeResolutionTiles", Set.of(1));
        set(pack, "scaled_textures", new HashMap<>());
        set(pack, "scaledlock", new Object());

        TexturePack scaled = pack.resampleTexturePack(4);

        assertEquals(4 * 4, scaled.getTileARGB(0).length);
        assertArrayEquals(atlas, scaled.getTileARGB(1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
