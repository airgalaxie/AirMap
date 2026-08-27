package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorTest {
    @Test
    void rgbShadingPreservesTextureAlpha() {
        Color color = new Color();
        color.setARGB(0x60C08040);

        color.blendRGB(0xFF808080);

        assertEquals(0x60, color.getAlpha(), "RGB shading must not make translucent texture pixels opaque");
        assertEquals(0x60604020, color.getARGB());
    }
}
