package org.dynmap;

import org.dynmap.RenderPerformanceMetrics.Window;
import org.dynmap.RendererPerformanceController.Profile;

/** Platform-neutral, one-variable-at-a-time experiments around the existing render pipeline. */
final class AdaptiveRenderController {
    enum Mode {
        ADAPTIVE, OBSERVE, LEGACY;

        static Mode parse(String value) {
            return switch (value == null ? "adaptive" : value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "observe" -> OBSERVE;
                case "legacy" -> LEGACY;
                default -> ADAPTIVE;
            };
        }
    }

    record Settings(int workers, int updateIntervalMillis, int chunksPerTick,
                    int fullDelayMillis, boolean paused) { }
    record Input(double tps, double processCpu, double systemCpu, double heap,
                 double gcFraction, Window work, boolean updatesPending) { }
    record Decision(Settings settings, String reason, double tilesPerSecond,
                    double dataShare, double outputCpuShare) { }
    private record Baseline(double rate, double tps, double cpu, double external, double updateShare) { }

    private final int maximumWorkers;
    private final int maximumChunks;
    private final long experimentNanos;
    private final RenderSafetyGovernor safety = new RenderSafetyGovernor();
    private Settings settings;
    private Settings previous;
    private Baseline baseline;
    private boolean reducing;
    private int nextAxis;
    private int trialAxis;
    private final long[] retryAfter = new long[3];
    private final int[] failedProbes = new int[3];
    private long cooldownUntil;
    private long elapsed, full, updates, data, output, outputCpu;
    private double tpsSum, cpuSum, externalSum;
    private int tpsCount, cpuCount, externalCount;
    private boolean cpuKnown = true;
    private double lastReductionTps = Double.NaN;

    AdaptiveRenderController(int processors, int configuredWorkers, int configuredChunks, Profile profile) {
        maximumWorkers = Math.max(1, configuredWorkers > 0
                ? Math.min(processors, configuredWorkers) : processors);
        maximumChunks = Math.max(5, configuredChunks);
        experimentNanos = (profile == Profile.CONSERVATIVE ? 60L : profile == Profile.AGGRESSIVE ? 15L : 30L)
                * 1_000_000_000L;
        settings = new Settings(1, 250, Math.min(100, maximumChunks), 0, false);
    }

    Settings initialSettings() { return settings; }
    int maximumWorkers() { return maximumWorkers; }

    Decision observe(long now, Input input) {
        Decision proposal = update(now, input);
        if (previous != null) {
            settings = previous;
            previous = null;
            baseline = null;
        }
        return proposal == null ? null : new Decision(proposal.settings,
                "observe only (not applied): " + proposal.reason, proposal.tilesPerSecond,
                proposal.dataShare, proposal.outputCpuShare);
    }

    Decision update(long now, Input input) {
        boolean paused = safety.update(input.heap, input.gcFraction);
        if (paused != settings.paused) {
            settings = new Settings(1, 1000, Math.min(25, maximumChunks), 250, paused);
            previous = null;
            baseline = null;
            resetWindow();
            cooldownUntil = now + experimentNanos;
            return decision(paused ? "safety: sustained heap and GC pressure" : "safety: recovered; restart at minimum", input.work);
        }
        if (paused) return null;

        Window w = input.work;
        elapsed += w.elapsedNanos();
        full += w.fullTiles();
        updates += w.updateTiles();
        data += w.dataNanos();
        output += w.outputNanos();
        outputCpu += w.outputCpuNanos();
        cpuKnown &= w.cpuKnown();
        if (valid(input.tps)) { tpsSum += Math.min(20, input.tps); tpsCount++; }
        if (valid(input.processCpu)) { cpuSum += input.processCpu; cpuCount++; }
        if (valid(input.systemCpu) && valid(input.processCpu)) {
            externalSum += Math.max(0, input.systemCpu - input.processCpu);
            externalCount++;
        }
        if (elapsed < experimentNanos) return null;
        Window aggregate = new Window(elapsed, full, updates, data, output, outputCpu, cpuKnown,
                w.active(), w.pending());
        Baseline current = new Baseline(aggregate.tilesPerSecond(), mean(tpsSum, tpsCount),
                mean(cpuSum, cpuCount), mean(externalSum, externalCount),
                (double) updates / Math.max(1, full + updates));
        resetWindow();
        if (aggregate.completed() == 0 || (!w.pending() && w.active() == 0)) {
            // An idle window is not evidence that a worker/rate change failed.
            previous = null;
            baseline = null;
            return decision("hold: no comparable render demand", aggregate);
        }

        if (previous != null) {
            boolean externalChanged = valid(current.external) && valid(baseline.external)
                    && Math.abs(current.external - baseline.external) > 0.15;
            boolean mixChanged = Math.abs(current.updateShare - baseline.updateShare) > 0.25;
            boolean healthWorse = valid(current.tps) && valid(baseline.tps) && current.tps < baseline.tps - 0.5;
            boolean cpuImproved = valid(current.cpu) && valid(baseline.cpu) && current.cpu < baseline.cpu - 0.02;
            boolean useful = reducing
                    ? !healthWorse && (valid(current.tps) && valid(baseline.tps) && current.tps > baseline.tps + 0.5
                        || current.rate > baseline.rate * 1.03
                        || current.rate >= baseline.rate * 0.97 && cpuImproved)
                    : current.rate > baseline.rate * 1.03 && !healthWorse;
            String reason;
            if (externalChanged || mixChanged) {
                settings = previous;
                reason = "rollback: external load or workload mix changed; result inconclusive";
            } else if (!useful) {
                settings = previous;
                if (!reducing && trialAxis >= 0) {
                    int failures = Math.min(4, ++failedProbes[trialAxis]);
                    retryAfter[trialAxis] = now + Math.min(900, 60L << failures) * 1_000_000_000L;
                }
                reason = reducing ? "rollback: reduction did not improve health, throughput or CPU efficiency"
                        : "rollback: no throughput gain or tick health worsened";
            } else {
                if (!reducing && trialAxis >= 0) failedProbes[trialAxis] = 0;
                reason = reducing ? "accept: reduction improved health, throughput or CPU efficiency"
                        : "accept: higher sustained throughput";
            }
            previous = null;
            baseline = null;
            cooldownUntil = now + (reducing ? 120_000_000_000L : experimentNanos);
            return decision(reason, aggregate);
        }
        if (now < cooldownUntil) return decision("hold: settling after experiment", aggregate);

        boolean lowTps = valid(current.tps) && current.tps < 18;
        // A low baseline alone does not justify endlessly reducing an unrelated workload.
        boolean tryReduction = lowTps && (Double.isNaN(lastReductionTps)
                || current.tps < lastReductionTps - 1.0);
        if (valid(current.tps) && current.tps >= 19) lastReductionTps = Double.NaN;
        if (input.heap >= 0.90 && input.gcFraction >= 0.05) tryReduction = true;
        Settings candidate;
        String reason;
        if (tryReduction) {
            if (lowTps) lastReductionTps = current.tps;
            candidate = reduce(aggregate.dataShare(), current.updateShare);
            reason = "probe down: measure whether less AirMap work helps";
        } else {
            candidate = increase(now, input.updatesPending);
            reason = "probe up: measure additional capacity";
        }
        if (candidate.equals(settings)) return decision("hold: capacity bound or probe cooldown", aggregate);
        previous = settings;
        baseline = current;
        reducing = tryReduction;
        settings = candidate;
        return decision(reason, aggregate);
    }

    private Settings reduce(double dataShare, double updateShare) {
        if (dataShare > 0.5 && settings.chunksPerTick > 5) {
            return new Settings(settings.workers, settings.updateIntervalMillis,
                    Math.max(5, settings.chunksPerTick / 2), settings.fullDelayMillis, false);
        }
        if (settings.workers > 1) {
            return new Settings(Math.max(1, settings.workers * 3 / 4), settings.updateIntervalMillis,
                    settings.chunksPerTick, settings.fullDelayMillis, false);
        }
        if (updateShare > 0.5) {
            return new Settings(1, Math.min(2000, Math.max(25, settings.updateIntervalMillis * 2)),
                    settings.chunksPerTick, settings.fullDelayMillis, false);
        }
        return new Settings(1, settings.updateIntervalMillis, settings.chunksPerTick,
                Math.min(1000, Math.max(25, settings.fullDelayMillis * 2)), false);
    }

    private Settings increase(long now, boolean updatesPending) {
        trialAxis = -1;
        if (settings.fullDelayMillis > 0) {
            return new Settings(settings.workers, settings.updateIntervalMillis, settings.chunksPerTick,
                    settings.fullDelayMillis / 2, false);
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            int axis = nextAxis++ % 3;
            if (now < retryAfter[axis]) continue;
            trialAxis = axis;
            switch (axis) {
                case 0:
                    if (settings.workers < maximumWorkers)
                        return new Settings(settings.workers + 1, settings.updateIntervalMillis,
                                settings.chunksPerTick, 0, false);
                    break;
                case 1:
                    if (updatesPending && settings.updateIntervalMillis > 0)
                        return new Settings(settings.workers, settings.updateIntervalMillis / 2,
                                settings.chunksPerTick, 0, false);
                    break;
                default:
                    if (settings.chunksPerTick < maximumChunks)
                        return new Settings(settings.workers, settings.updateIntervalMillis,
                                Math.min(maximumChunks, settings.chunksPerTick + 25), 0, false);
            }
        }
        return settings;
    }

    private Decision decision(String reason, Window work) {
        return new Decision(settings, reason, work.tilesPerSecond(), work.dataShare(), work.outputCpuShare());
    }

    private void resetWindow() {
        elapsed = full = updates = data = output = outputCpu = 0;
        tpsSum = cpuSum = externalSum = 0;
        tpsCount = cpuCount = externalCount = 0;
        cpuKnown = true;
    }

    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0; }
    private static double mean(double sum, int count) { return count == 0 ? -1 : sum / count; }
}
