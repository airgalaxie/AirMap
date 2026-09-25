package org.dynmap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Coordinates render work for logically equal tiles across full/radius and
 * invalidation driven render paths.
 */
final class TileRenderCoordinator {
    static final class WorkResult {
        final boolean continueRender;
        final boolean completed;
        final boolean hasData;
        final boolean updated;
        final long renderNanos;
        final boolean renderCalled;

        WorkResult(boolean continueRender, boolean completed, boolean hasData, boolean updated,
                long renderNanos, boolean renderCalled) {
            this.continueRender = continueRender;
            this.completed = completed;
            this.hasData = hasData;
            this.updated = updated;
            this.renderNanos = renderNanos;
            this.renderCalled = renderCalled;
        }
    }

    static final class UpdateResult {
        final WorkResult result;
        final boolean requeue;

        UpdateResult(WorkResult result, boolean requeue) {
            this.result = result;
            this.requeue = requeue;
        }
    }

    private static final class Running {
        final String mapName; // null means all compatible map outputs
        final long revision;
        boolean finished;
        boolean successful;
        WorkResult result;

        Running(String mapName, long revision) {
            this.mapName = mapName;
            this.revision = revision;
        }

        boolean covers(String requestedMapName) {
            return mapName == null || (requestedMapName != null && mapName.equals(requestedMapName));
        }
    }

    private static final class State {
        long desiredRevision;
        long allOutputsCompletedRevision;
        WorkResult allOutputsResult;
        boolean updateScheduled;
        int participants;
        Running running;
    }

    private final Object lock = new Object();
    private final Map<MapTile, State> states = new HashMap<MapTile, State>();

    /** Register one newly consumed invalidation.  True means a queue item is needed. */
    boolean requestUpdate(MapTile tile) {
        synchronized (lock) {
            State state = states.computeIfAbsent(tile, ignored -> new State());
            state.desiredRevision++;
            if (!state.updateScheduled) {
                state.updateScheduled = true;
                return true;
            }
            return false;
        }
    }

    /** Forget a queued update which was explicitly purged before it could run. */
    void cancelUpdate(MapTile tile) {
        synchronized (lock) {
            State state = states.get(tile);
            if (state == null) return;
            state.updateScheduled = false;
            state.allOutputsCompletedRevision = state.desiredRevision;
            removeIfIdle(tile, state);
        }
    }

    WorkResult executeFull(MapTile tile, String mapName, Supplier<WorkResult> work) {
        State state = join(tile);
        try {
            final long requiredRevision;
            synchronized (lock) {
                requiredRevision = state.desiredRevision;
            }
            return execute(tile, state, mapName, requiredRevision, true, work);
        } finally {
            leave(tile, state);
        }
    }

    UpdateResult executeUpdate(MapTile tile, Supplier<WorkResult> work) {
        State state = join(tile);
        WorkResult result;
        boolean requeue;
        try {
            final long requiredRevision;
            synchronized (lock) {
                requiredRevision = state.desiredRevision;
            }
            result = execute(tile, state, null, requiredRevision, false, work);
            synchronized (lock) {
                requeue = !result.completed || state.allOutputsCompletedRevision < state.desiredRevision;
                if (!requeue) {
                    state.updateScheduled = false;
                }
            }
        } finally {
            leave(tile, state);
        }
        return new UpdateResult(result, requeue);
    }

    private State join(MapTile tile) {
        synchronized (lock) {
            State state = states.computeIfAbsent(tile, ignored -> new State());
            state.participants++;
            return state;
        }
    }

    private void leave(MapTile tile, State state) {
        synchronized (lock) {
            state.participants--;
            removeIfIdle(tile, state);
        }
    }

    private void removeIfIdle(MapTile tile, State state) {
        if (state.participants == 0 && state.running == null && !state.updateScheduled) {
            states.remove(tile, state);
        }
    }

    private WorkResult execute(MapTile tile, State state, String mapName, long requiredRevision,
            boolean force, Supplier<WorkResult> work) {
        Running claim;
        while (true) {
            synchronized (lock) {
                if (!force && state.allOutputsResult != null
                        && state.allOutputsCompletedRevision >= requiredRevision) {
                    return state.allOutputsResult;
                }
                Running running = state.running;
                if (running == null) {
                    claim = new Running(mapName, state.desiredRevision);
                    state.running = claim;
                    break;
                }
                if (running.covers(mapName) && running.revision >= requiredRevision) {
                    waitFor(running);
                    if (running.successful) {
                        return running.result;
                    }
                } else {
                    waitFor(running);
                }
            }
        }

        WorkResult result = null;
        boolean successful = false;
        try {
            result = work.get();
            successful = result.completed;
            return result;
        } finally {
            synchronized (lock) {
                claim.result = result;
                claim.successful = successful;
                claim.finished = true;
                if (successful && claim.mapName == null) {
                    state.allOutputsCompletedRevision = Math.max(state.allOutputsCompletedRevision, claim.revision);
                    state.allOutputsResult = result;
                }
                state.running = null;
                lock.notifyAll();
            }
        }
    }

    private void waitFor(Running running) {
        boolean interrupted = false;
        while (!running.finished) {
            try {
                lock.wait();
            } catch (InterruptedException ix) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
