package org.dynmap;

final class RendererPerformanceController {
    static final long SAMPLE_INTERVAL_NANOS = 5_000_000_000L;
    static final long RECOVERY_INTERVAL_NANOS = 30_000_000_000L;

    enum Profile {
        OFF,
        CONSERVATIVE,
        NORMAL,
        AGGRESSIVE;

        static Profile fromConfig(String value) {
            if (value == null) return NORMAL;
            switch (value.trim().toLowerCase()) {
                case "false": return OFF;
                case "conservative": return CONSERVATIVE;
                case "aggressive": return AGGRESSIVE;
                case "true":
                case "normal":
                default: return NORMAL;
            }
        }

        static boolean isValidConfigValue(String value) {
            if (value == null) return true;
            switch (value.trim().toLowerCase()) {
                case "false":
                case "true":
                case "conservative":
                case "normal":
                case "aggressive":
                    return true;
                default:
                    return false;
            }
        }
    }

    enum LoadLevel {
        FREE,
        NORMAL,
        LOADED,
        CRITICAL
    }

    static final class Settings {
        final LoadLevel level;
        final int renderIntervalMillis;
        final int acceleratedIntervalMillis;
        final int updateTiles;
        final long fullRenderIntervalMillis;
        final int chunksPerTick;
        final boolean pauseFullRender;
        final boolean pauseZoomOut;

        Settings(LoadLevel level, int renderIntervalMillis, int acceleratedIntervalMillis,
                 int updateTiles, long fullRenderIntervalMillis, int chunksPerTick,
                 boolean pauseFullRender, boolean pauseZoomOut) {
            this.level = level;
            this.renderIntervalMillis = renderIntervalMillis;
            this.acceleratedIntervalMillis = acceleratedIntervalMillis;
            this.updateTiles = updateTiles;
            this.fullRenderIntervalMillis = fullRenderIntervalMillis;
            this.chunksPerTick = chunksPerTick;
            this.pauseFullRender = pauseFullRender;
            this.pauseZoomOut = pauseZoomOut;
        }
    }

    private final int processors;
    private final Profile profile;
    private LoadLevel level = LoadLevel.NORMAL;
    private int adaptiveTiles = 1;
    private long lastSampleNanos = Long.MIN_VALUE;
    private long recoveryCandidateSince = Long.MIN_VALUE;
    private long capacityCandidateSince = Long.MIN_VALUE;

    RendererPerformanceController(int processors) {
        this(processors, Profile.NORMAL);
    }

    RendererPerformanceController(int processors, Profile profile) {
        this.processors = Math.max(1, processors);
        this.profile = profile;
    }

    Settings initialSettings() {
        return settingsFor(level);
    }

    boolean shouldSample(long nowNanos) {
        return lastSampleNanos == Long.MIN_VALUE || nowNanos - lastSampleNanos >= SAMPLE_INTERVAL_NANOS;
    }

    Settings update(long nowNanos, double tps, double processCpuLoad, double heapUsage, int players) {
        if (lastSampleNanos != Long.MIN_VALUE && nowNanos - lastSampleNanos < SAMPLE_INTERVAL_NANOS) {
            return null;
        }
        lastSampleNanos = nowNanos;

        LoadLevel desired = classify(tps, processCpuLoad, heapUsage, players);
        if (desired.ordinal() > level.ordinal()) {
            level = desired;
            adaptiveTiles = 1;
            recoveryCandidateSince = Long.MIN_VALUE;
            capacityCandidateSince = Long.MIN_VALUE;
            return settingsFor(level);
        }
        if (desired.ordinal() < level.ordinal()) {
            if (recoveryCandidateSince == Long.MIN_VALUE) {
                recoveryCandidateSince = nowNanos;
            }
            if (nowNanos - recoveryCandidateSince >= recoveryIntervalNanos()) {
                level = LoadLevel.values()[level.ordinal() - 1];
                recoveryCandidateSince = nowNanos;
                capacityCandidateSince = nowNanos;
                return settingsFor(level);
            }
        } else {
            recoveryCandidateSince = Long.MIN_VALUE;
            int maximumTiles = maximumAdaptiveTiles(level);
            if (adaptiveTiles < maximumTiles) {
                if (capacityCandidateSince == Long.MIN_VALUE) {
                    capacityCandidateSince = nowNanos;
                } else if (nowNanos - capacityCandidateSince >= recoveryIntervalNanos()) {
                    adaptiveTiles++;
                    capacityCandidateSince = nowNanos;
                    return settingsFor(level);
                }
            } else {
                capacityCandidateSince = Long.MIN_VALUE;
            }
        }
        return null;
    }

    private LoadLevel classify(double tps, double cpu, double heap, int players) {
        if (tps < criticalTps() || atLeast(cpu, criticalCpu()) || atLeast(heap, criticalHeap())) {
            return LoadLevel.CRITICAL;
        }
        if (tps < loadedTps() || atLeast(cpu, loadedCpu()) || atLeast(heap, loadedHeap())
                || (players >= Math.max(4, processors) && atLeast(cpu, 0.75))) {
            return LoadLevel.LOADED;
        }
        if (players > 0 || tps < 19.7 || atLeast(cpu, 0.70) || atLeast(heap, 0.70)) {
            return LoadLevel.NORMAL;
        }
        return LoadLevel.FREE;
    }

    private static boolean atLeast(double value, double threshold) {
        return value >= 0.0 && value >= threshold;
    }

    private Settings settingsFor(LoadLevel selected) {
        switch (selected) {
            case FREE:
                int freeTiles = clamp(Math.max(2, adaptiveTiles), 2, maximumAdaptiveTiles(selected));
                return new Settings(selected, 250, 100, freeTiles,
                        0, clamp(75 + (freeTiles * 25), 100, maximumChunks(true)), false, false);
            case LOADED:
                return new Settings(selected, 1500, 750, 1, 200, 50, false, false);
            case CRITICAL:
                return new Settings(selected, 2500, 1000, 1, 500, 25, true, true);
            case NORMAL:
            default:
                int normalTiles = clamp(adaptiveTiles, 1, maximumAdaptiveTiles(selected));
                return new Settings(selected, 750, 250, normalTiles,
                        50, clamp(75 + (normalTiles * 25), 100, maximumChunks(false)), false, false);
        }
    }

    private int maximumAdaptiveTiles(LoadLevel selected) {
        if (selected == LoadLevel.FREE) {
            int maximum = profile == Profile.CONSERVATIVE ? 4 : profile == Profile.AGGRESSIVE ? 12 : 8;
            int divisor = profile == Profile.CONSERVATIVE ? 3 : 2;
            return clamp(processors / divisor, 2, maximum);
        }
        if (selected == LoadLevel.NORMAL) {
            int maximum = profile == Profile.CONSERVATIVE ? 4 : profile == Profile.AGGRESSIVE ? 12 : 8;
            int divisor = profile == Profile.CONSERVATIVE ? 6 : profile == Profile.AGGRESSIVE ? 3 : 4;
            return clamp(processors / divisor, 1, maximum);
        }
        return 1;
    }

    private long recoveryIntervalNanos() {
        if (profile == Profile.CONSERVATIVE) return 60_000_000_000L;
        if (profile == Profile.AGGRESSIVE) return 15_000_000_000L;
        return RECOVERY_INTERVAL_NANOS;
    }

    private double loadedTps() {
        return profile == Profile.CONSERVATIVE ? 19.5 : profile == Profile.AGGRESSIVE ? 18.8 : 19.2;
    }

    private double criticalTps() {
        return profile == Profile.CONSERVATIVE ? 19.0 : profile == Profile.AGGRESSIVE ? 18.0 : 18.5;
    }

    private double loadedCpu() {
        return profile == Profile.CONSERVATIVE ? 0.82 : profile == Profile.AGGRESSIVE ? 0.92 : 0.88;
    }

    private double criticalCpu() {
        return profile == Profile.CONSERVATIVE ? 0.92 : profile == Profile.AGGRESSIVE ? 0.98 : 0.95;
    }

    private double loadedHeap() {
        return profile == Profile.CONSERVATIVE ? 0.78 : profile == Profile.AGGRESSIVE ? 0.88 : 0.82;
    }

    private double criticalHeap() {
        return profile == Profile.CONSERVATIVE ? 0.88 : profile == Profile.AGGRESSIVE ? 0.94 : 0.90;
    }

    private int maximumChunks(boolean free) {
        if (profile == Profile.CONSERVATIVE) return free ? 175 : 150;
        if (profile == Profile.AGGRESSIVE) return free ? 350 : 300;
        return free ? 250 : 200;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
