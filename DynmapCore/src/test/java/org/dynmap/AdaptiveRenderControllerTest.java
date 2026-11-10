package org.dynmap;

import org.junit.jupiter.api.Test;
import org.dynmap.RendererPerformanceController.Profile;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRenderControllerTest {
    @Test void tpsProtectionHasHysteresisAndKeepsMissingMeasurementsUnknown() {
        var c = controller(4);
        assertFalse(c.updateTpsStage(20));
        assertTrue(c.updateTpsStage(17.9));
        assertEquals(AdaptiveRenderController.TpsStage.SLOW, c.tpsStage());
        assertFalse(c.updateTpsStage(18.2));
        assertTrue(c.updateTpsStage(11.9));
        assertFalse(c.updateTpsStage(12.4));
        assertFalse(c.updateTpsStage(Double.NaN));
        assertFalse(c.updateTpsStage(-1));
        assertTrue(c.updateTpsStage(12.5));
        assertEquals(AdaptiveRenderController.TpsStage.SLOW, c.tpsStage());
        assertFalse(c.updateTpsStage(18.49));
        assertTrue(c.updateTpsStage(18.5));
        assertEquals(AdaptiveRenderController.TpsStage.NORMAL, c.tpsStage());
    }

    @Test void protectionBoundsWorkWithoutDestroyingLearnedSettingsOrSafety() {
        var c = controller(8);
        c.update(WINDOW, healthy(30));
        var learned = c.initialSettings();
        c.updateTpsStage(11);
        var protectedSettings = c.protectedSettings(learned);
        assertEquals(1, protectedSettings.workers());
        assertEquals(5, protectedSettings.chunksPerTick());
        assertEquals(1000, protectedSettings.updateIntervalMillis());
        assertFalse(protectedSettings.paused(), "TPS protection must allow bounded updates");
        for (int i = 1; i <= 3; i++) c.update(i * WINDOW, input(11, .5, .5, .98, .3, 1, 1, 1, 1, true));
        assertTrue(c.protectedSettings(c.initialSettings()).paused(), "heap/GC safety wins over updates");
    }

    @Test void changingLimitsDiscardsOldTrialsWithoutRestartingController() {
        var c = controller(8);
        c.update(WINDOW, healthy(30)); // Trial has two workers.
        c.setMaximumWorkers(1);
        assertEquals(1, c.maximumWorkers());
        assertEquals(1, c.initialSettings().workers());
        for (int i = 2; i < 8; i++) assertEquals(1, c.update(i * WINDOW, healthy(100)).settings().workers());
        c.setMaximumWorkers(4);
        c.setTpsLimits(16, 10);
        c.updateTpsStage(17);
        assertEquals(AdaptiveRenderController.TpsStage.NORMAL, c.tpsStage());
        assertEquals(16, c.slowTps());
        assertEquals(10, c.criticalTps());
    }

    private static final long WINDOW = 30_000_000_000L;

    private static AdaptiveRenderController controller(int processors) {
        return new AdaptiveRenderController(processors, 0, 200, Profile.NORMAL);
    }

    private static AdaptiveRenderController.Input input(double tps, double cpu, double system,
            double heap, double gc, long full, long updates, long data, long output, boolean pending) {
        return new AdaptiveRenderController.Input(tps, cpu, system, heap, gc,
                new RenderPerformanceMetrics.Window(WINDOW, full, updates, data, output, output / 2,
                        true, pending ? 1 : 0, pending), updates > 0);
    }

    private static AdaptiveRenderController.Input healthy(long tiles) {
        return input(20, .5, .55, .5, .01, tiles, 0, 1, 10, true);
    }

    @Test void requiresTwoUsefulWindowsRatherThanAcceptingASinglePeak() {
        var c = controller(4);
        assertEquals(2, c.update(WINDOW, healthy(30)).settings().workers());
        assertTrue(c.update(2 * WINDOW, healthy(60)).reason().startsWith("verify"));
        var rollback = c.update(3 * WINDOW, healthy(30));
        assertEquals(1, rollback.settings().workers());
        assertTrue(rollback.reason().startsWith("rollback"));
        assertEquals(0, rollback.settings().fullDelayMillis());
    }

    @Test void discoversDifferentSustainableCapacitiesQuicklyAndHoldsThemAcrossLongRuns() {
        for (int[] machine : new int[][] {{4, 2}, {32, 8}, {12, 5}}) {
            var c = controller(machine[0]);
            for (int step = 1; step <= 32; step++) simulate(c, step, machine[1], 0);
            assertEquals(machine[1], c.initialSettings().workers(), "bounded search must refine a coarse overshoot");
            var point = c.initialSettings();
            double sum = 0, squares = 0, tpsSum = 0;
            int changes = 0, pauses = 0;
            for (int step = 33; step <= 232; step++) {
                var decision = simulate(c, step, machine[1], step % 3 - 1);
                if (!point.equals(decision.settings())) changes++;
                if (decision.settings().paused() || decision.settings().fullDelayMillis() != 0) pauses++;
                sum += decision.tilesPerSecond();
                squares += decision.tilesPerSecond() * decision.tilesPerSecond();
                tpsSum += 20;
            }
            double mean = sum / 200;
            double variation = Math.sqrt(Math.max(0, squares / 200 - mean * mean)) / mean;
            assertEquals(0, changes, "elapsed time alone must not restart probing");
            assertEquals(0, pauses);
            assertEquals(20, tpsSum / 200);
            assertEquals(machine[1] * 37.0 / 30, mean, 0.01);
            assertTrue(variation < 0.03, "evaluate continuous throughput, not a single peak");
        }
    }

    private static AdaptiveRenderController.Decision simulate(AdaptiveRenderController c, int step, int usefulWorkers, int jitter) {
        var s = c.initialSettings();
        long tiles = Math.min(s.workers(), usefulWorkers) * 37L + jitter;
        double tps = 20;
        if (s.workers() > usefulWorkers) { tiles = tiles * 4 / 5; tps = 19.1; }
        c.updateTpsStage(tps);
        var result = c.update(step * WINDOW, input(tps, .6, .65, .5, .01, tiles, 0, 1, 10, true));
        assertNotNull(result);
        assertFalse(result.settings().paused());
        assertEquals(0, result.settings().fullDelayMillis());
        return result;
    }

    @Test void noisyIntervalsDoNotTriggerAcceleration() {
        var c = controller(8);
        for (int cycle = 0; cycle < 12; cycle++) {
            AdaptiveRenderController.Decision result = null;
            for (int sample = 1; sample <= 6; sample++) {
                long count = sample % 2 == 0 ? 91 : 7;
                var work = new RenderPerformanceMetrics.Window(WINDOW / 6, count, 0, 1, 10, 5, true, 1, true);
                result = c.update((cycle * 6L + sample) * WINDOW / 6,
                        new AdaptiveRenderController.Input(20, .5, .55, .5, .01, work, false));
            }
            assertNotNull(result);
            assertEquals(1, result.settings().workers());
            assertTrue(result.reason().contains("stable headroom"));
        }
    }

    @Test void regularSparseCompletionsCanStillDiscoverCapacityFromFiveSecondSamples() {
        var c = controller(8);
        AdaptiveRenderController.Decision result = null;
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int sample = 1; sample <= 6; sample++) {
                long count = cycle == 0 ? sample % 2 : 1;
                var work = new RenderPerformanceMetrics.Window(WINDOW / 6, count, 0, 1, 10, 5, true, 1, true);
                result = c.update((cycle * 6L + sample) * WINDOW / 6,
                        new AdaptiveRenderController.Input(20, .5, .55, .5, .01, work, false));
            }
            assertNotNull(result);
            assertTrue(result.settings().workers() >= 2, "regular sparse completions are not excess workload noise");
        }
        assertTrue(result.reason().startsWith("accept"));
    }

    @Test void longRunningTilesKeepElapsedTimeAndUnverifiedTrialsAcrossEmptyWindows() {
        var c = controller(8);
        var waiting = c.update(WINDOW, healthy(0));
        assertTrue(waiting.reason().contains("in-flight"));
        var trial = c.update(2 * WINDOW, healthy(1));
        assertEquals(1.0 / 60, trial.tilesPerSecond());
        assertEquals(2, trial.settings().workers());
        assertEquals(2, c.update(3 * WINDOW, healthy(0)).settings().workers());
        assertTrue(c.update(4 * WINDOW, healthy(2)).reason().startsWith("verify"));
        assertEquals(2, c.update(5 * WINDOW, healthy(0)).settings().workers());
        assertTrue(c.update(6 * WINDOW, healthy(2)).reason().startsWith("accept"));
    }

    @Test void sustainedDataPressureUsesASmallReductionWithoutFullrenderPacing() {
        var c = controller(1);
        c.setTpsLimits(14, 8);
        for (int step = 1; step <= 12; step++) c.update(step * WINDOW, input(20, .6, .65, .5, .01, 30, 0, 20, 1, true));
        int original = c.initialSettings().chunksPerTick();
        for (int step = 13; step <= 24; step++) {
            boolean reduced = c.initialSettings().chunksPerTick() < original;
            var result = c.update(step * WINDOW, input(reduced ? 18 : 17, .6, .65, .5, .01, 30, 0, 20, 1, true));
            assertTrue(result.settings().chunksPerTick() >= original * 7 / 8);
            assertFalse(result.settings().paused());
            assertEquals(0, result.settings().fullDelayMillis());
        }
        assertTrue(c.initialSettings().chunksPerTick() < original);
    }

    @Test void mildSustainedHeapGcPressureDoesNotAccelerateOrRepeatedlyCutTheSameBudget() {
        var c = controller(8);
        int changes = 0;
        var last = c.initialSettings();
        for (int step = 1; step <= 18; step++) {
            boolean reduced = c.initialSettings().chunksPerTick() < 100;
            var result = c.update(step * WINDOW,
                    input(20, reduced ? .5 : .6, reduced ? .55 : .65, .92, .06, 30, 0, 20, 1, true));
            if (!result.settings().equals(last)) changes++;
            last = result.settings();
            assertEquals(1, last.workers());
            assertFalse(last.paused());
            assertEquals(0, last.fullDelayMillis());
        }
        assertEquals(1, changes);
        assertEquals(88, last.chunksPerTick());
    }

    @Test void furtherPressureDuringEarlySafetyRecoveryDoesNotLeaveRecoveryWaitingForever() {
        var c = controller(8);
        for (int step = 1; step <= 3; step++) c.update(step * WINDOW, input(20, .6, .65, .98, .3, 30, 0, 20, 1, true));
        for (int step = 4; step <= 9; step++) c.update(step * WINDOW, healthy(0));
        assertEquals(25, c.initialSettings().chunksPerTick());
        for (int step = 10; step <= 13; step++) {
            boolean reduced = c.initialSettings().chunksPerTick() < 25;
            c.update(step * WINDOW, input(20, reduced ? .5 : .6, reduced ? .55 : .65, .92, .06, 75, 0, 20, 1, true));
        }
        assertEquals(22, c.initialSettings().chunksPerTick());
        for (int step = 14; step <= 44; step++) {
            long tiles = c.initialSettings().chunksPerTick() * 3L;
            c.update(step * WINDOW, input(20, .5, .55, .5, .01, tiles, 0, 20, 1, true));
        }
        assertTrue(c.initialSettings().chunksPerTick() >= 100);
        assertFalse(c.initialSettings().paused());
    }

    @Test void changingWindowComplexityDoesNotRepeatedlyReopenSettledSearch() {
        var c = controller(8);
        for (int step = 1; step <= 32; step++) simulate(c, step, 4, 0);
        var point = c.initialSettings();
        for (int step = 33; step <= 132; step++) {
            long tiles = step % 2 == 0 ? 60 : 240;
            var decision = c.update(step * WINDOW, healthy(tiles));
            assertEquals(point, decision.settings(), "opposite changes must not accumulate as sustained demand");
        }
    }

    @Test void toleratesPlayerJoinThenReducesSustainedPressureGentlyAndRecoversToProvenPoint() {
        var c = controller(8);
        for (int step = 1; step <= 32; step++) simulate(c, step, 4, 0);
        var point = c.initialSettings();
        var transientLoad = c.update(33 * WINDOW, input(19.1, .65, .95, .5, .01, 120, 0, 1, 10, true));
        assertEquals(point, transientLoad.settings());
        for (int step = 34; step <= 40; step++) assertEquals(point, simulate(c, step, 4, 0).settings());
        int changes = 0;
        var last = point;
        for (int step = 41; step <= 65; step++) {
            int workers = c.initialSettings().workers();
            double tps = workers >= 4 ? 19.0 : 19.7;
            var result = c.update(step * WINDOW, input(tps, .6, .9, .5, .01, workers * 37L, 0, 1, 10, true));
            if (!last.equals(result.settings())) changes++;
            last = result.settings();
            assertFalse(last.paused());
            assertEquals(0, last.fullDelayMillis());
            assertTrue(last.workers() >= 3, "moderate overload must not collapse capacity to minimum");
        }
        assertEquals(3, last.workers());
        assertEquals(1, changes, "hold the relieved point throughout sustained external load");
        for (int step = 66; step <= 80; step++) {
            int workers = c.initialSettings().workers();
            c.update(step * WINDOW, input(20, .6, .65, .5, .01, workers * 37L, 0, 1, 10, true));
        }
        assertEquals(point, c.initialSettings());
    }

    @Test void tpsRecoveryReturnsGraduallyWithoutForgettingTheProvenDestination() {
        var c = controller(12);
        for (int step = 1; step <= 32; step++) simulate(c, step, 6, 0);
        var target = c.initialSettings();
        assertEquals(6, target.workers());
        c.updateTpsStage(11);
        assertEquals(AdaptiveRenderController.TpsStage.CRITICAL, c.tpsStage());
        assertFalse(c.protectedSettings(target).paused(), "critical updates keep their existing bounded budget");
        c.updateTpsStage(20);
        assertEquals(1, c.initialSettings().workers(), "TPS recovery must not jump straight to the old load");
        int lastWorkers = 1;
        for (int step = 33; step <= 58; step++) {
            var before = c.initialSettings();
            long tiles = Math.max(1, before.workers() * before.chunksPerTick() * 37L / target.chunksPerTick());
            var result = c.update(step * WINDOW, input(20, .5, .55, .5, .01, tiles, 0, 1, 10, true));
            var next = result.settings();
            assertTrue(next.workers() <= lastWorkers + Math.max(1, lastWorkers / 2));
            assertTrue(next.workers() <= target.workers());
            assertFalse(next.paused());
            assertEquals(0, next.fullDelayMillis());
            lastWorkers = next.workers();
        }
        assertEquals(target, c.initialSettings());
    }

    @Test void noNormalOptimizationAddsFullrenderPausesOrUsesAHardcodedTpsTarget() {
        var c = new AdaptiveRenderController(1, 1, 5, Profile.NORMAL);
        c.setTpsLimits(14, 8);
        c.update(WINDOW, healthy(30));
        for (int step = 2; step <= 20; step++) {
            c.updateTpsStage(17);
            var result = c.update(step * WINDOW, input(17, .95, .97, .5, .01, 30, 0, 1, 10, true));
            assertEquals(AdaptiveRenderController.TpsStage.NORMAL, c.tpsStage());
            assertFalse(result.settings().paused());
            assertEquals(0, result.settings().fullDelayMillis());
        }
    }

    @Test void dataAndUpdateBottlenecksUseTheirExistingControls() {
        var dataBound = controller(4);
        var result = dataBound.update(WINDOW, input(20, .5, .55, .5, .01, 30, 0, 20, 1, true));
        assertEquals(1, result.settings().workers());
        assertEquals(150, result.settings().chunksPerTick());
        var updateBound = controller(4);
        result = updateBound.update(WINDOW, input(20, .5, .55, .5, .01, 0, 30, 1, 10, true));
        assertEquals(1, result.settings().workers());
        assertTrue(result.settings().updateIntervalMillis() < 250);
        assertEquals(0, result.settings().fullDelayMillis());
    }

    @Test void persistentWorkloadMixChangeReopensSearchAfterTwoWindows() {
        var c = controller(4);
        for (int step = 1; step <= 32; step++) simulate(c, step, 2, 0);
        var point = c.initialSettings();
        var updateWork = input(20, .5, .55, .5, .01, 0, 74, 1, 10, true);
        assertEquals(point, c.update(33 * WINDOW, updateWork).settings());
        assertTrue(c.update(34 * WINDOW, updateWork).settings().updateIntervalMillis() < point.updateIntervalMillis());
    }

    @Test void externalLoadChangeCannotEstablishAFalseCapacityLimit() {
        var c = controller(4);
        c.update(WINDOW, healthy(30));
        var decision = c.update(2 * WINDOW, input(20, .5, .9, .5, .01, 40, 0, 1, 10, true));
        assertEquals(1, decision.settings().workers());
        assertTrue(decision.reason().contains("inconclusive"));
        for (int step = 3; step < 5; step++) assertEquals(1, c.update(step * WINDOW, healthy(30)).settings().workers());
        assertEquals(2, c.update(5 * WINDOW, healthy(30)).settings().workers());
    }

    @Test void adminLimitsRemainBindingAndDiscardUnverifiedTrials() {
        var c = controller(16);
        for (int step = 1; step <= 32; step++) simulate(c, step, 5, 0);
        c.setMaximumWorkers(2);
        for (int step = 33; step <= 48; step++) assertTrue(simulate(c, step, 5, 0).settings().workers() <= 2);
        c.setMaximumWorkers(8);
        for (int step = 49; step <= 80; step++) simulate(c, step, 5, 0);
        assertEquals(5, c.initialSettings().workers());
        c.updateTpsStage(11);
        c.setMaximumWorkers(1);
        c.updateTpsStage(20);
        for (int step = 81; step <= 100; step++) assertEquals(1, simulate(c, step, 5, 0).settings().workers());
    }

    @Test void observesWithoutPretendingProposedChangesWereApplied() {
        var c = controller(4);
        for (int step = 1; step <= 6; step++) {
            var result = c.observe(step * WINDOW, healthy(30));
            assertTrue(result.reason().startsWith("observe only"));
            assertEquals(1, c.initialSettings().workers());
        }
    }

    @Test void idleAndUnknownMeasurementsDoNotEstablishCapacity() {
        var c = controller(4);
        var idle = c.update(WINDOW, input(-1, -1, -1, -1, -1, 0, 0, 0, 0, false));
        assertEquals(1, idle.settings().workers());
        assertTrue(idle.reason().contains("no comparable"));
        var unknown = c.update(2 * WINDOW, input(-1, -1, -1, -1, -1, 30, 0, 1, 10, true));
        assertEquals(1, unknown.settings().workers());
        assertFalse(unknown.settings().paused());
    }

    @Test void highCpuAloneHoldsAccelerationWithoutPausingOrReducing() {
        var c = controller(8);
        for (int step = 1; step <= 5; step++) {
            var result = c.update(step * WINDOW, input(20, .97, .99, .5, .01, 30, 0, 1, 10, true));
            assertEquals(1, result.settings().workers());
            assertFalse(result.settings().paused());
            assertEquals(0, result.settings().fullDelayMillis());
        }
        assertEquals(2, c.update(6 * WINDOW, healthy(30)).settings().workers());
    }

    @Test void safetyIsConfirmedIndependentlyAndRestartsAtMinimum() {
        var c = controller(8);
        for (int i = 1; i <= 3; i++) {
            var decision = c.update(i * WINDOW, input(20, .3, .4, .98, .3, 30, 0, 1, 10, true));
            assertNotNull(decision);
            assertEquals(i == 3, decision.settings().paused());
        }
        AdaptiveRenderController.Decision recovered = null;
        for (int i = 4; i <= 9; i++) {
            var decision = c.update(i * WINDOW, healthy(0));
            if (decision != null) recovered = decision;
        }
        assertNotNull(recovered);
        assertFalse(recovered.settings().paused());
        assertEquals(1, recovered.settings().workers());
        assertEquals(0, recovered.settings().fullDelayMillis());
    }
}
