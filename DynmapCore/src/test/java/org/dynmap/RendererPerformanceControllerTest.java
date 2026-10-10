package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RendererPerformanceControllerTest {
    @Test
    void profileConfigurationKeepsBooleanCompatibility() {
        assertSame(RendererPerformanceController.Profile.OFF,
                RendererPerformanceController.Profile.fromConfig("false"));
        assertSame(RendererPerformanceController.Profile.NORMAL,
                RendererPerformanceController.Profile.fromConfig("true"));
        assertSame(RendererPerformanceController.Profile.NORMAL,
                RendererPerformanceController.Profile.fromConfig("normal"));
        assertSame(RendererPerformanceController.Profile.CONSERVATIVE,
                RendererPerformanceController.Profile.fromConfig("conservative"));
        assertSame(RendererPerformanceController.Profile.AGGRESSIVE,
                RendererPerformanceController.Profile.fromConfig("aggressive"));
        assertSame(RendererPerformanceController.Profile.NORMAL,
                RendererPerformanceController.Profile.fromConfig("unknown"));
        assertFalse(RendererPerformanceController.Profile.isValidConfigValue("unknown"));
    }

    @Test
    void profilesApplyDifferentTpsReserves() {
        RendererPerformanceController conservative = new RendererPerformanceController(
                8, RendererPerformanceController.Profile.CONSERVATIVE);
        RendererPerformanceController.Settings conservativeSettings =
                conservative.update(0, 19.3, 0.30, 0.30, 0);
        assertEquals(RendererPerformanceController.LoadLevel.LOADED, conservativeSettings.level);

        RendererPerformanceController aggressive = new RendererPerformanceController(
                8, RendererPerformanceController.Profile.AGGRESSIVE);
        assertNull(aggressive.update(0, 19.3, 0.30, 0.30, 1));
    }

    @Test
    void overloadThrottlesImmediatelyAndRecoveryIsGradual() {
        RendererPerformanceController controller = new RendererPerformanceController(8);

        RendererPerformanceController.Settings critical = controller.update(0, 18.0, 0.40, 0.40, 1);
        assertEquals(RendererPerformanceController.LoadLevel.CRITICAL, critical.level);
        assertTrue(critical.pauseFullRender);
        assertTrue(critical.pauseZoomOut);

        assertNull(controller.update(RendererPerformanceController.SAMPLE_INTERVAL_NANOS,
                20.0, 0.20, 0.30, 0));
        long firstRecovery = RendererPerformanceController.SAMPLE_INTERVAL_NANOS
                + RendererPerformanceController.RECOVERY_INTERVAL_NANOS;
        RendererPerformanceController.Settings loaded = controller.update(
                firstRecovery,
                20.0, 0.20, 0.30, 0);
        assertEquals(RendererPerformanceController.LoadLevel.LOADED, loaded.level);
        assertFalse(loaded.pauseFullRender);

        RendererPerformanceController.Settings normal = controller.update(
                firstRecovery + RendererPerformanceController.RECOVERY_INTERVAL_NANOS,
                20.0, 0.20, 0.30, 0);
        assertEquals(RendererPerformanceController.LoadLevel.NORMAL, normal.level);

        RendererPerformanceController.Settings free = controller.update(
                firstRecovery + 2 * RendererPerformanceController.RECOVERY_INTERVAL_NANOS,
                20.0, 0.20, 0.30, 0);
        assertEquals(RendererPerformanceController.LoadLevel.FREE, free.level);
        assertEquals(2, free.updateTiles);
        assertEquals(125, free.chunksPerTick);
    }

    @Test
    void playersKeepHealthyServerAtNormalThroughput() {
        RendererPerformanceController controller = new RendererPerformanceController(8);

        RendererPerformanceController.Settings settings = controller.update(0, 20.0, 0.20, 0.30, 1);

        assertNull(settings, "normal is the safe initial level while players are online");
    }

    @Test
    void cpuAndHeapPressureAreAdapterIndependentInputs() {
        RendererPerformanceController cpuController = new RendererPerformanceController(4);
        RendererPerformanceController.Settings cpu = cpuController.update(0, 20.0, 0.96, 0.30, 0);
        assertEquals(RendererPerformanceController.LoadLevel.CRITICAL, cpu.level);

        RendererPerformanceController heapController = new RendererPerformanceController(4);
        RendererPerformanceController.Settings heap = heapController.update(0, 20.0, -1.0, 0.83, 0);
        assertEquals(RendererPerformanceController.LoadLevel.LOADED, heap.level);
    }

    @Test
    void stableModernMachineFindsMoreCapacityWithoutAdapterKnowledge() {
        RendererPerformanceController controller = new RendererPerformanceController(32);

        assertNull(controller.update(0, 20.0, 0.30, 0.30, 1));
        RendererPerformanceController.Settings secondTile = controller.update(
                RendererPerformanceController.RECOVERY_INTERVAL_NANOS, 20.0, 0.30, 0.30, 1);
        assertEquals(2, secondTile.updateTiles);
        assertEquals(125, secondTile.chunksPerTick);

        RendererPerformanceController.Settings thirdTile = controller.update(
                2 * RendererPerformanceController.RECOVERY_INTERVAL_NANOS, 20.0, 0.30, 0.30, 1);
        assertEquals(3, thirdTile.updateTiles);

        RendererPerformanceController.Settings throttled = controller.update(
                2 * RendererPerformanceController.RECOVERY_INTERVAL_NANOS
                        + RendererPerformanceController.SAMPLE_INTERVAL_NANOS,
                19.0, 0.30, 0.30, 1);
        assertEquals(RendererPerformanceController.LoadLevel.LOADED, throttled.level);
        assertEquals(1, throttled.updateTiles);
    }
}
