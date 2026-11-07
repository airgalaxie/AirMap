package org.dynmap;

import org.junit.jupiter.api.Test;
import org.dynmap.RendererPerformanceController.Profile;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRenderControllerTest {
    private static final long WINDOW = 30_000_000_000L;

    private static AdaptiveRenderController controller(int processors) {
        return new AdaptiveRenderController(processors, 0, 200, Profile.NORMAL);
    }

    private static AdaptiveRenderController.Input input(double tps, double cpu, double system,
            double heap, double gc, long full, long updates, long data, long output, boolean pending) {
        return new AdaptiveRenderController.Input(tps, cpu, system, heap, gc,
                new RenderPerformanceMetrics.Window(WINDOW, full, updates, data, output, output / 2,
                        true, 1, pending), updates > 0);
    }

    private static AdaptiveRenderController.Input healthy(long tiles) {
        return input(20, .5, .55, .5, .01, tiles, 0, 1, 10, true);
    }

    @Test void highCpuAndLowTpsDoNotPauseLikeLegacy() {
        RendererPerformanceController legacy = new RendererPerformanceController(4);
        assertTrue(legacy.update(0, 17, .99, .5, 1).pauseFullRender);
        var adaptive = controller(4);
        var decision = adaptive.update(WINDOW, input(17, .99, .99, .5, .01, 30, 0, 1, 10, true));
        assertFalse(decision.settings().paused());
        assertEquals(1, decision.settings().workers());
        assertEquals(25, decision.settings().fullDelayMillis());
    }

    @Test void acceptsMeasuredGainAndRollsBackUnproductiveCapacity() {
        var controller = controller(4);
        assertEquals(2, controller.update(WINDOW, healthy(30)).settings().workers());
        assertTrue(controller.update(2 * WINDOW, healthy(50)).reason().startsWith("accept"));
        // Settling has elapsed; next applicable axis is the data budget (no updates pending).
        assertEquals(125, controller.update(3 * WINDOW, healthy(50)).settings().chunksPerTick());
        var rollback = controller.update(4 * WINDOW, healthy(50));
        assertEquals(100, rollback.settings().chunksPerTick());
        assertTrue(rollback.reason().startsWith("rollback"));
    }

    @Test void rollbackAlsoChecksMinecraftHealth() {
        var controller = controller(8);
        controller.update(WINDOW, healthy(30));
        var decision = controller.update(2 * WINDOW,
                input(18, .8, .85, .5, .01, 60, 0, 1, 10, true));
        assertEquals(1, decision.settings().workers());
        assertTrue(decision.reason().startsWith("rollback"));
    }

    @Test void lowTpsReductionIsReversedIfItOnlyLosesThroughput() {
        var controller = controller(4);
        controller.update(WINDOW, input(12, .5, .55, .5, .01, 30, 0, 1, 10, true));
        var decision = controller.update(2 * WINDOW,
                input(12, .3, .35, .5, .01, 15, 0, 1, 10, true));
        assertEquals(0, decision.settings().fullDelayMillis());
        assertTrue(decision.reason().contains("did not improve"));
        assertFalse(decision.settings().paused());
    }

    @Test void lowTpsReductionCanImproveHealthAndIsThenKept() {
        var controller = controller(4);
        controller.update(WINDOW, input(12, .5, .55, .5, .01, 30, 0, 1, 10, true));
        var decision = controller.update(2 * WINDOW,
                input(14, .4, .45, .5, .01, 25, 0, 1, 10, true));
        assertEquals(25, decision.settings().fullDelayMillis());
        assertTrue(decision.reason().startsWith("accept"));
    }

    @Test void preservesThroughputWhenReductionActuallySavesCpu() {
        var controller = controller(4);
        controller.update(WINDOW, input(12, .5, .55, .5, .01, 30, 0, 1, 10, true));
        var decision = controller.update(2 * WINDOW,
                input(12, .4, .45, .5, .01, 30, 0, 1, 10, true));
        assertEquals(25, decision.settings().fullDelayMillis());
        assertTrue(decision.reason().startsWith("accept"));
    }

    @Test void stableExternalLoadDoesNotPreventUsingRemainingCapacity() {
        var controller = controller(8);
        var decision = controller.update(WINDOW,
                input(20, .1, .6, .5, .01, 30, 0, 1, 10, true));
        assertEquals(2, decision.settings().workers());
        decision = controller.update(2 * WINDOW,
                input(20, .2, .7, .5, .01, 50, 0, 1, 10, true));
        assertTrue(decision.reason().startsWith("accept"));
    }

    @Test void searchesDifferentHardwareCapacitiesWithoutStopping() {
        for (int[] machine : new int[][] {{4, 2}, {32, 8}}) {
            var controller = controller(machine[0]);
            var settings = controller.initialSettings();
            for (int step = 1; step <= 300; step++) {
                int usefulWorkers = Math.min(settings.workers(), machine[1]);
                long tiles = usefulWorkers * 30L;
                if (settings.workers() > machine[1]) tiles = tiles * 9 / 10;
                var decision = controller.update(step * WINDOW,
                        input(20, .7, .75, .5, .01, tiles, 0, 1, 10, true));
                settings = decision.settings();
                assertFalse(settings.paused());
                if (step > 200) {
                    assertTrue(settings.workers() >= machine[1]);
                    assertTrue(settings.workers() <= machine[1] + 1);
                }
            }
        }
    }

    @Test void worldDataAndUpdateRateAreSeparateExistingControls() {
        var dataBound = controller(4);
        var decision = dataBound.update(WINDOW,
                input(17, .5, .55, .5, .01, 30, 0, 20, 1, true));
        assertEquals(50, decision.settings().chunksPerTick());
        assertEquals(0, decision.settings().fullDelayMillis());
        var updates = controller(4);
        decision = updates.update(WINDOW, input(17, .5, .55, .5, .01, 0, 30, 1, 10, true));
        assertEquals(500, decision.settings().updateIntervalMillis());
        assertFalse(decision.settings().paused());
    }

    @Test void externalLoadChangeMakesExperimentInconclusive() {
        var controller = controller(4);
        controller.update(WINDOW, healthy(30));
        var decision = controller.update(2 * WINDOW,
                input(20, .5, .9, .5, .01, 40, 0, 1, 10, true));
        assertEquals(1, decision.settings().workers());
        assertTrue(decision.reason().contains("inconclusive"));
    }

    @Test void observesWithoutPretendingProposedChangesWereApplied() {
        var controller = controller(4);
        assertEquals(2, controller.observe(WINDOW, healthy(30)).settings().workers());
        assertEquals(1, controller.initialSettings().workers());
        assertTrue(controller.observe(2 * WINDOW, healthy(60)).reason().startsWith("observe only"));
        assertEquals(1, controller.initialSettings().workers());
    }

    @Test void idleOrUnknownMeasurementsDoNotPauseOrEstablishCapacity() {
        var controller = controller(4);
        var decision = controller.update(WINDOW,
                input(-1, -1, -1, -1, -1, 0, 0, 0, 0, false));
        assertEquals(1, decision.settings().workers());
        assertFalse(decision.settings().paused());
        assertTrue(decision.reason().contains("no comparable"));
    }

    @Test void worksWithOneProcessorAndHonorsConfiguredCeilings() {
        var small = controller(1);
        for (int i = 1; i <= 10; i++) {
            var decision = small.update(i * WINDOW, healthy(30));
            assertTrue(decision.settings().workers() <= 1);
            assertFalse(decision.settings().paused());
        }
        var limited = new AdaptiveRenderController(32, 2, 50, Profile.NORMAL);
        assertEquals(2, limited.maximumWorkers());
        assertEquals(50, limited.initialSettings().chunksPerTick());
    }

    @Test void safetyIsConfirmedIndependentlyAndRestartsAtMinimum() {
        var controller = controller(8);
        for (int i = 1; i <= 3; i++) {
            var decision = controller.update(i * WINDOW,
                    input(20, .3, .4, .98, .3, 30, 0, 1, 10, true));
            assertEquals(i == 3, decision.settings().paused());
        }
        AdaptiveRenderController.Decision recovered = null;
        for (int i = 4; i <= 9; i++) {
            var decision = controller.update(i * WINDOW, healthy(0));
            if (decision != null) recovered = decision;
        }
        assertNotNull(recovered);
        assertFalse(recovered.settings().paused());
        assertEquals(1, recovered.settings().workers());
    }
}
