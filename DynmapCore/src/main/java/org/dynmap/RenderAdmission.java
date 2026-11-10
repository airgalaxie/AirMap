package org.dynmap;

/** Non-blocking, shared admission for existing full/update jobs. Never wait in the render pool. */
final class RenderAdmission {
    private final java.util.function.LongSupplier clock;
    private int active;
    private boolean preferFull;
    private record Waiting(boolean full, long lastAttemptNanos) { }
    private final java.util.LinkedHashMap<Object, Waiting> waiting = new java.util.LinkedHashMap<>();

    RenderAdmission() { this(System::nanoTime); }
    RenderAdmission(java.util.function.LongSupplier clock) { this.clock = clock; }

    int acquire(boolean full, int requested, int capacity) {
        return acquire(Boolean.valueOf(full), full, requested, capacity);
    }

    int acquire(Object job, boolean full, int requested, int capacity) {
        return acquire(job, full, requested, capacity, false);
    }

    synchronized int acquire(Object job, boolean full, int requested, int capacity, boolean preferUpdates) {
        capacity = Math.max(1, capacity);
        long now = clock.getAsLong();
        // An expired caller rejoins at the end rather than retaining its old position.
        Waiting old = waiting.get(job);
        if (old != null && now - old.lastAttemptNanos >= 1_000_000_000L) waiting.remove(job);
        waiting.put(job, new Waiting(full, now));
        int available = capacity - active;
        Object next = null;
        boolean preferred = false;
        boolean wantFull = !preferUpdates && preferFull;
        // Purge stale jobs and select the first preferred waiter in one pass.
        for (var iterator = waiting.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Waiting request = entry.getValue();
            if (now - request.lastAttemptNanos >= 1_000_000_000L) iterator.remove();
            else if (!preferred && (next == null || request.full == wantFull)) {
                next = entry.getKey();
                preferred = request.full == wantFull;
            }
        }
        if (!job.equals(next)) return 0;
        // Keep one slot available for another waiting job where possible.
        if (waiting.size() > 1 && available > 1) available--;
        int granted = Math.max(0, Math.min(requested, available));
        if (granted > 0) {
            active += granted;
            waiting.remove(job);
        }
        return granted;
    }

    synchronized void cancel(Object job) { waiting.remove(job); }

    synchronized void release(boolean full, int count) {
        active -= count;
        preferFull = !full;
    }

    synchronized int active() {
        return active;
    }

    Reservation reservation(boolean full, int granted) {
        return new Reservation(this, full, granted);
    }

    /** A child keeps its own slot even if its parent is interrupted while joining. */
    static final class Reservation implements AutoCloseable {
        private final RenderAdmission owner;
        private final boolean full;
        private int count;

        private Reservation(RenderAdmission owner, boolean full, int count) {
            this.owner = owner;
            this.full = full;
            this.count = count;
        }

        synchronized Reservation split() {
            if (count <= 1) throw new IllegalStateException("No child slot reserved");
            count--;
            return new Reservation(owner, full, 1);
        }

        @Override public synchronized void close() {
            if (count > 0) {
                owner.release(full, count);
                count = 0;
            }
        }
    }
}
