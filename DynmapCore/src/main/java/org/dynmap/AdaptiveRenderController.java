package org.dynmap;

import org.dynmap.RenderPerformanceMetrics.Window;
import org.dynmap.RendererPerformanceController.Profile;

/** Measured capacity search followed by stable operation; callers serialize updates and admin changes. */
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
    private record Baseline(double rate, double noise, double tps, double cpu, double external,
                            double updateShare, double dataShare) { }

    enum TpsStage { NORMAL, SLOW, CRITICAL }
    private int maximumWorkers;
    private double slowTps = 18, criticalTps = 12;
    private volatile TpsStage tpsStage = TpsStage.NORMAL;
    private final int maximumChunks;
    private final long experimentNanos;
    private final RenderSafetyGovernor safety = new RenderSafetyGovernor();
    private Settings settings, previous, proven, recoveryTarget;
    private Baseline baseline, reference, confirmation, recoveryReference, contextCandidate;
    private boolean reducing, settled;
    private int trialAxis, pressureWindows, contextWindows, recoveryWindows;
    private int workerLimit, chunkLimit, intervalFloor;
    private long cooldownUntil;
    private long elapsed, full, updates, data, output, outputCpu;
    private double tpsSum, cpuSum, externalSum, rateSum, rateSquares;
    private int tpsCount, cpuCount, externalCount, rateCount;
    private boolean cpuKnown = true;
    private double lastReductionTps = Double.NaN;

    AdaptiveRenderController(int processors, int configuredWorkers, int configuredChunks, Profile profile) {
        maximumWorkers = Math.max(1, configuredWorkers > 0
                ? Math.min(processors, configuredWorkers) : processors);
        maximumChunks = Math.max(5, configuredChunks);
        workerLimit = maximumWorkers;
        chunkLimit = maximumChunks;
        experimentNanos = (profile == Profile.CONSERVATIVE ? 60L : profile == Profile.AGGRESSIVE ? 15L : 30L)
                * 1_000_000_000L;
        settings = new Settings(1, 250, Math.min(100, maximumChunks), 0, false);
    }

    Settings initialSettings() { return settings; }
    int maximumWorkers() { return maximumWorkers; }
    TpsStage tpsStage() { return tpsStage; }
    double slowTps() { return slowTps; }
    double criticalTps() { return criticalTps; }

    void setMaximumWorkers(int maximum) {
        if (maximum == maximumWorkers) return;
        maximumWorkers = maximum;
        // An interrupted, unverified trial must not become the new proven point.
        settings = bounded(previous != null ? previous : settings);
        if (proven != null) proven = bounded(proven);
        if (recoveryTarget != null) recoveryTarget = bounded(recoveryTarget);
        discardExperiment();
        reopenSearch();
    }

    void setTpsLimits(double slow, double critical) {
        if (slowTps == slow && criticalTps == critical) return;
        slowTps = slow;
        criticalTps = critical;
        if (previous != null) settings = previous;
        discardExperiment();
        reopenSearch();
    }

    boolean updateTpsStage(double tps) {
        if (!valid(tps)) return false;
        TpsStage before = tpsStage;
        if (tps < criticalTps || (before == TpsStage.CRITICAL && tps < criticalTps + 0.5)) tpsStage = TpsStage.CRITICAL;
        else if (tps < slowTps || (before != TpsStage.NORMAL && tps < slowTps + 0.5)) tpsStage = TpsStage.SLOW;
        else tpsStage = TpsStage.NORMAL;
        if (before == tpsStage) return false;
        if (before == TpsStage.NORMAL) rememberRecoveryPoint();
        if (previous != null) settings = previous;
        discardExperiment();
        if (tpsStage == TpsStage.NORMAL && !settings.paused) {
            // Keep the destination, but never jump straight from the protection budget to it.
            settings = new Settings(1, Math.max(1000, settings.updateIntervalMillis),
                    Math.min(25, settings.chunksPerTick), 0, false);
            reference = null;
        }
        return true;
    }

    Settings protectedSettings(Settings base) {
        if (tpsStage == TpsStage.NORMAL) return base;
        return new Settings(1, Math.max(1000, base.updateIntervalMillis),
                Math.min(5, base.chunksPerTick), Math.max(1000, base.fullDelayMillis), base.paused);
    }

    private Settings bounded(Settings value) {
        if (value.workers <= maximumWorkers) return value;
        return new Settings(Math.min(value.workers, maximumWorkers), value.updateIntervalMillis,
                value.chunksPerTick, value.fullDelayMillis, value.paused);
    }

    private void rememberRecoveryPoint() {
        if (recoveryTarget == null) recoveryTarget = proven != null ? proven : settings;
        recoveryReference = null;
        lastReductionTps = Double.NaN; // Protection changes invalidate the old reduction comparison.
    }

    private void discardExperiment() {
        previous = null;
        baseline = confirmation = null;
        resetWindow();
        cooldownUntil = 0;
        pressureWindows = contextWindows = recoveryWindows = 0;
        contextCandidate = null;
    }

    private void reopenSearch() {
        settled = false;
        reference = null;
        lastReductionTps = Double.NaN;
        workerLimit = maximumWorkers;
        chunkLimit = maximumChunks;
        intervalFloor = 0;
    }

    Decision observe(long now, Input input) {
        Decision proposal = update(now, input);
        if (previous != null) {
            settings = previous;
            previous = null;
            baseline = confirmation = null;
        }
        return proposal == null ? null : new Decision(proposal.settings,
                "observe only (not applied): " + proposal.reason, proposal.tilesPerSecond,
                proposal.dataShare, proposal.outputCpuShare);
    }

    Decision update(long now, Input input) {
        boolean paused = safety.update(input.heap, input.gcFraction);
        if (paused != settings.paused) {
            rememberRecoveryPoint();
            settings = new Settings(1, 1000, Math.min(25, maximumChunks), paused ? 250 : 0, paused);
            discardExperiment();
            reference = null;
            return decision(paused ? "safety: sustained heap and GC pressure"
                    : "safety: recovered; bounded return to proven capacity", input.work);
        }
        if (paused) return null;
        if (tpsStage != TpsStage.NORMAL) return decision("TPS protection: " + tpsStage, input.work);

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
        if (w.elapsedNanos() > 0) {
            double rate = w.tilesPerSecond();
            rateSum += rate;
            rateSquares += rate * rate;
            rateCount++;
        }
        if (elapsed < experimentNanos) return null;
        if (full + updates == 0 && (w.pending() || w.active() > 0)) {
            // An expensive tile may span a whole window. Retain elapsed time and the
            // trial instead of treating zero completed tiles as idleness or failure.
            return decision("hold: awaiting in-flight completions", w);
        }
        Window aggregate = new Window(elapsed, full, updates, data, output, outputCpu, cpuKnown, w.active(), w.pending());
        if (!w.pending() && w.active() == 0) {
            if (previous != null) settings = previous;
            discardExperiment();
            return decision("hold: no comparable render demand", aggregate);
        }
        double average = mean(rateSum, rateCount);
        // Relative standard error above counting noise: sparse, regular completions
        // must not look like unstable load just because a tile takes more than 5s.
        double noise = rateCount < 2 || average <= 0 ? 0
                : Math.sqrt(Math.max(0, (rateSquares / rateCount - average * average)
                        / ((rateCount - 1) * average * average)
                        - (double) settings.workers / aggregate.completed()));
        Baseline current = new Baseline(aggregate.tilesPerSecond(), noise, mean(tpsSum, tpsCount),
                mean(cpuSum, cpuCount), mean(externalSum, externalCount),
                (double) updates / aggregate.completed(), aggregate.dataShare());
        resetWindow();
        boolean memoryPressure = input.heap >= 0.90 && input.gcFraction >= 0.05;
        if (previous != null) return evaluateTrial(now, current, aggregate, memoryPressure);
        if (proven == null) proven = settings;
        if (reference == null) reference = current;

        boolean contextChanged = differentContext(current, reference)
                || Math.abs(current.dataShare - reference.dataShare) > 0.20
                || Math.abs(current.rate / Math.max(1e-9, reference.rate) - 1) > 0.20;
        boolean sameChange = contextCandidate != null && !differentContext(current, contextCandidate)
                && Math.signum(current.rate - reference.rate) == Math.signum(contextCandidate.rate - reference.rate);
        contextWindows = contextChanged ? sameChange ? contextWindows + 1 : 1 : 0;
        contextCandidate = contextChanged ? current : null;
        boolean tpsWorse = valid(current.tps) && valid(reference.tps) && current.tps < reference.tps - 0.5;
        boolean pressure = tpsWorse || memoryPressure;
        pressureWindows = pressure ? pressureWindows + 1 : 0;
        // Player joins / short foreign load: observe several windows, rather than chase it.
        if (contextChanged && contextWindows < 2) return decision("hold: workload/load change not yet sustained", aggregate);
        if (now < cooldownUntil) return decision("hold: settling after change", aggregate);
        if (pressure && pressureWindows < 2) return decision("hold: await sustained pressure", aggregate);

        if (pressureWindows >= 2 && (Double.isNaN(lastReductionTps) || current.tps < lastReductionTps - 0.5)) {
            Settings candidate = reduce(current.dataShare, current.updateShare);
            if (!candidate.equals(settings)) {
                if (recoveryTarget == null) recoveryTarget = proven;
                if (recoveryReference == null) recoveryReference = reference;
                lastReductionTps = current.tps;
                return startTrial(candidate, current, true, "ease down: sustained pressure", aggregate);
            }
        }
        if (pressure) return decision("hold: no further useful reduction", aggregate);
        if (recoveryTarget != null) {
            boolean relief = recoveryReference == null || (valid(current.tps)
                    && valid(recoveryReference.tps) && current.tps >= recoveryReference.tps - 0.25
                    && (!valid(current.external) || !valid(recoveryReference.external)
                        || current.external <= recoveryReference.external + 0.05));
            boolean healthy = valid(current.tps) && current.tps >= slowTps + 0.5
                    && (!valid(current.cpu) || current.cpu < 0.90)
                    && (input.heap < 0.90 || !valid(input.heap)) && (input.gcFraction < 0.05 || !valid(input.gcFraction));
            recoveryWindows = healthy && relief ? recoveryWindows + 1 : 0;
            if (recoveryWindows < 2) return decision("hold: await sustained recovery", aggregate);
            Settings candidate = recover();
            if (!candidate.equals(settings)) return startTrial(candidate, current, false, "recover: towards proven capacity", aggregate);
            recoveryTarget = null;
            recoveryReference = null;
            settled = true;
            reference = current;
            contextWindows = 0;
        } else if (contextWindows >= 2) {
            reopenSearch();
            reference = current;
            contextWindows = 0;
        }
        if (settled) return decision("hold: proven sustainable capacity", aggregate);
        // CPU headroom is a reason to stop accelerating, never a reason to pause rendering.
        if (!valid(current.tps) || current.noise > 0.10 || (valid(current.cpu) && current.cpu >= 0.90)) {
            return decision("hold: insufficient stable headroom", aggregate);
        }
        Settings candidate = increase(current.dataShare, input.updatesPending && current.updateShare > 0.5);
        if (candidate.equals(settings)) {
            settled = true;
            reference = current;
            return decision("hold: proven sustainable capacity", aggregate);
        }
        return startTrial(candidate, current, false, "probe up: bounded capacity step", aggregate);
    }

    private Decision startTrial(Settings candidate, Baseline current, boolean down, String reason, Window work) {
        previous = settings;
        baseline = current;
        confirmation = null;
        reducing = down;
        settings = candidate;
        return decision(reason, work);
    }

    private Decision evaluateTrial(long now, Baseline current, Window work, boolean memoryPressure) {
        boolean contextChanged = differentContext(current, baseline);
        boolean healthWorse = valid(current.tps) && valid(baseline.tps) && current.tps < baseline.tps - 0.5;
        double margin = Math.max(0.03, 2 * Math.max(current.noise, baseline.noise));
        boolean gain = current.rate > baseline.rate * (1 + margin);
        boolean retainedRate = current.rate >= baseline.rate * (1 - Math.min(0.10, margin));
        boolean safeRestore = recoveryTarget != null && retainedRate
                && (!valid(current.cpu) || !valid(baseline.cpu) || current.cpu <= baseline.cpu + 0.02);
        boolean useful = !contextChanged && !healthWorse && current.noise <= 0.10 && (reducing
                ? (valid(current.tps) && valid(baseline.tps) && current.tps > baseline.tps + 0.5)
                    || gain
                    || retainedRate && valid(current.cpu) && valid(baseline.cpu) && current.cpu < baseline.cpu - 0.02
                : valid(current.tps) && (gain || safeRestore));
        if (useful && confirmation == null) {
            confirmation = current;
            return decision("verify: await a second sustained window", work);
        }
        // Both consecutive windows must be useful, with comparable load and no unstable peak.
        boolean accepted = useful && confirmation != null && !differentContext(current, confirmation);
        String reason;
        if (accepted) {
            proven = settings;
            reference = current;
            pressureWindows = contextWindows = 0;
            if (reducing) { settled = true; recoveryWindows = 0; }
            reason = reducing ? "accept: sustained relief at lower load" : "accept: sustained throughput gain";
        } else {
            Settings rejected = settings;
            settings = previous;
            if (contextChanged) {
                cooldownUntil = now + 3 * experimentNanos;
                reason = "rollback: external load or workload mix changed; result inconclusive";
            } else {
                if (recoveryTarget != null && !reducing) { recoveryTarget = null; recoveryReference = null; settled = true; }
                else if (!reducing) {
                    // Refine harmful coarse steps; a flat/noisy result closes this axis.
                    boolean refine = healthWorse || current.rate < baseline.rate * (1 - margin);
                    if (trialAxis == 0) workerLimit = refine ? Math.min(workerLimit, rejected.workers - 1) : settings.workers;
                    else if (trialAxis == 1) intervalFloor = refine ? Math.max(intervalFloor, rejected.updateIntervalMillis + 1) : settings.updateIntervalMillis;
                    else chunkLimit = refine ? Math.min(chunkLimit, rejected.chunksPerTick - 1) : settings.chunksPerTick;
                }
                cooldownUntil = now + experimentNanos;
                reason = "rollback: no sustained gain or tick health worsened";
            }
            if (reducing) { recoveryTarget = null; recoveryReference = null; settled = true; reference = baseline; }
        }
        previous = null;
        baseline = confirmation = null;
        // The two verified windows already measured this proven point. During initial
        // discovery there is no need to spend another window measuring it unchanged.
        if (accepted && !reducing && recoveryTarget == null
                && !memoryPressure && (!valid(current.cpu) || current.cpu < 0.90)) {
            Settings candidate = increase(current.dataShare, current.updateShare > 0.5);
            if (!candidate.equals(settings)) {
                return startTrial(candidate, current, false, "accept: sustained gain; next bounded step", work);
            }
            settled = true;
        }
        return decision(reason, work);
    }

    private static boolean differentContext(Baseline a, Baseline b) {
        return (valid(a.external) && valid(b.external) && Math.abs(a.external - b.external) > 0.15)
                || Math.abs(a.updateShare - b.updateShare) > 0.25;
    }

    private Settings reduce(double dataShare, double updateShare) {
        if (dataShare > 0.5 && settings.chunksPerTick > 5) {
            return new Settings(settings.workers, settings.updateIntervalMillis,
                    Math.max(5, settings.chunksPerTick - Math.max(1, settings.chunksPerTick / 8)), 0, false);
        }
        if (settings.workers > 1) {
            return new Settings(settings.workers - Math.max(1, settings.workers / 8), settings.updateIntervalMillis,
                    settings.chunksPerTick, 0, false);
        }
        if (updateShare > 0.5) {
            return new Settings(1, Math.min(2000, settings.updateIntervalMillis + Math.max(10, settings.updateIntervalMillis / 4)),
                    settings.chunksPerTick, 0, false);
        }
        return settings; // Never invent a fullrender pause to optimize throughput.
    }

    private Settings increase(double dataShare, boolean updateBound) {
        int preferred = dataShare > 0.5 ? 2 : updateBound ? 1 : 0;
        for (int i = 0; i < 3; i++) {
            int axis = (preferred + i) % 3;
            trialAxis = axis;
            switch (axis) {
                case 0:
                    if (settings.workers < workerLimit)
                        return new Settings(Math.min(workerLimit, settings.workers + Math.max(1,
                                workerLimit < maximumWorkers ? (workerLimit - settings.workers + 1) / 2 : settings.workers / 2)),
                                settings.updateIntervalMillis, settings.chunksPerTick, 0, false);
                    break;
                case 1:
                    if (updateBound && settings.updateIntervalMillis > intervalFloor)
                        return new Settings(settings.workers, Math.max(intervalFloor, settings.updateIntervalMillis - Math.max(1,
                                intervalFloor > 0 ? (settings.updateIntervalMillis - intervalFloor) / 2 : settings.updateIntervalMillis / 4)),
                                settings.chunksPerTick, 0, false);
                    break;
                default:
                    if (settings.chunksPerTick < chunkLimit)
                        return new Settings(settings.workers, settings.updateIntervalMillis,
                                Math.min(chunkLimit, settings.chunksPerTick + Math.max(1,
                                        chunkLimit < maximumChunks ? (chunkLimit - settings.chunksPerTick + 1) / 2 : settings.chunksPerTick / 2)), 0, false);
            }
        }
        return settings;
    }

    private Settings recover() {
        Settings target = recoveryTarget;
        return new Settings(Math.min(target.workers, settings.workers + Math.max(1, settings.workers / 2)),
                Math.max(target.updateIntervalMillis, settings.updateIntervalMillis - Math.max(1, settings.updateIntervalMillis / 4)),
                Math.min(target.chunksPerTick, settings.chunksPerTick + Math.max(1, settings.chunksPerTick / 2)), 0, false);
    }

    private Decision decision(String reason, Window work) {
        return new Decision(settings, reason, work.tilesPerSecond(), work.dataShare(), work.outputCpuShare());
    }

    private void resetWindow() {
        elapsed = full = updates = data = output = outputCpu = 0;
        tpsSum = cpuSum = externalSum = rateSum = rateSquares = 0;
        tpsCount = cpuCount = externalCount = rateCount = 0;
        cpuKnown = true;
    }

    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0; }
    private static double mean(double sum, int count) { return count == 0 ? -1 : sum / count; }
}
