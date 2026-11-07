package org.dynmap;

/** Safety is independent of throughput tuning. CPU/TPS alone never stop rendering. */
final class RenderSafetyGovernor {
    private int pressureSamples;
    private int recoverySamples;
    private boolean paused;

    boolean update(double heap, double gcFraction) {
        boolean pressure = Double.isFinite(heap) && Double.isFinite(gcFraction)
                && heap >= 0.97 && gcFraction >= 0.15;
        pressureSamples = pressure ? pressureSamples + 1 : 0;
        if (pressureSamples >= 3) paused = true;
        boolean recovered = heap >= 0 && heap < 0.88 && gcFraction >= 0 && gcFraction < 0.08;
        recoverySamples = recovered ? recoverySamples + 1 : 0;
        if (recoverySamples >= 6) paused = false;
        return paused;
    }
}
