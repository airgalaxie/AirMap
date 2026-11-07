package org.dynmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderPerformanceMetricsTest {
    @Test void countsActualExecutionsAndSeparatesDataWaitFromRenderAndOutput() {
        var metrics = new RenderPerformanceMetrics(0);
        metrics.data(100);
        metrics.output(true, 200, 100, true);
        metrics.output(false, 300, -1, false);
        var window = metrics.sample(1_000_000_000L, 1, true);
        assertEquals(1, window.fullTiles());
        assertEquals(0, window.updateTiles());
        assertEquals(100, window.dataNanos());
        assertEquals(500, window.outputNanos());
        assertEquals(1, window.tilesPerSecond());
        assertEquals(-1, window.outputCpuShare());
        assertEquals(0, metrics.sample(2_000_000_000L, 0, false).completed());
    }
}
