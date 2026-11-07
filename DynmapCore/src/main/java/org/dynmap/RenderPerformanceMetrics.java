package org.dynmap;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Small, coherent measurement windows; no image/storage behavior is changed. */
final class RenderPerformanceMetrics {
    record Window(long elapsedNanos, long fullTiles, long updateTiles, long dataNanos,
                  long outputNanos, long outputCpuNanos, boolean cpuKnown, int active,
                  boolean pending) {
        long completed() { return fullTiles + updateTiles; }
        double tilesPerSecond() { return completed() * 1.0e9 / elapsedNanos; }
        double dataShare() { return (double) dataNanos / Math.max(1L, dataNanos + outputNanos); }
        double outputCpuShare() { return cpuKnown ? (double) outputCpuNanos / Math.max(1L, outputNanos) : -1; }
    }

    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private long fullTiles, updateTiles, dataNanos, outputNanos, outputCpuNanos;
    private boolean cpuKnown = true;
    private long lastWindowNanos;

    RenderPerformanceMetrics(long now) { lastWindowNanos = now; }

    long threadCpuTime() {
        return threads.isCurrentThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled()
                ? threads.getCurrentThreadCpuTime() : -1;
    }

    synchronized void data(long nanos) { dataNanos += Math.max(0, nanos); }

    synchronized void output(boolean full, long nanos, long cpuNanos, boolean completed) {
        outputNanos += Math.max(0, nanos);
        if (cpuNanos >= 0) outputCpuNanos += cpuNanos;
        else cpuKnown = false;
        if (completed) {
            if (full) fullTiles++;
            else updateTiles++;
        }
    }

    synchronized Window sample(long now, int active, boolean pending) {
        Window result = new Window(Math.max(1, now - lastWindowNanos), fullTiles, updateTiles,
                dataNanos, outputNanos, outputCpuNanos, cpuKnown, active, pending);
        fullTiles = updateTiles = dataNanos = outputNanos = outputCpuNanos = 0;
        cpuKnown = true;
        lastWindowNanos = now;
        return result;
    }
}
