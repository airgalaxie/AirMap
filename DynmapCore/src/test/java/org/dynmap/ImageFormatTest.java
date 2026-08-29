package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.dynmap.MapType.ImageEncoding;
import org.dynmap.MapType.ImageFormat;
import org.junit.jupiter.api.Test;

class ImageFormatTest {
    @Test
    void resolvesConfiguredWebpFormatExactly() {
        ImageFormat fmt = ImageFormat.fromID("webp-q80");
        assertEquals(ImageFormat.FORMAT_WEBP80, fmt);
        assertEquals("webp-q80", fmt.getID());
        assertEquals(ImageEncoding.WEBP, fmt.getEncoding());
    }

    @Test
    void pngIsTheExistingFallbackFormat() {
        ImageFormat fmt = ImageFormat.fromID("png");
        assertEquals(ImageFormat.FORMAT_PNG, fmt);
        assertEquals("png", fmt.getID());
        assertEquals(ImageEncoding.PNG, fmt.getEncoding());
        assertEquals("png", fmt.getFileExt());
    }

    @Test
    void rejectsFormatIdsOutsideTheImplementedMatrix() {
        assertNull(ImageFormat.fromID("png-80"));
        assertNull(ImageFormat.fromID("png-q80"));
        assertNull(ImageFormat.fromID("bmp"));
        assertNull(ImageFormat.fromID(""));
    }
}