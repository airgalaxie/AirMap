package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynmap.utils.MapChunkCache;
import org.junit.jupiter.api.Test;

class TileRenderCoordinatorTest {
    private static final class TestTile extends MapTile {
        private final int id;

        TestTile(int id) {
            super(null);
            this.id = id;
        }

        @Override public boolean render(MapChunkCache cache, String mapname) { return true; }
        @Override public List<DynmapChunk> getRequiredChunks() { return Collections.emptyList(); }
        @Override public MapTile[] getAdjecentTiles() { return new MapTile[0]; }
        @Override public int getTileSize() { return 128; }
        @Override public boolean isBiomeDataNeeded() { return false; }
        @Override public boolean isHightestBlockYDataNeeded() { return false; }
        @Override public boolean isRawBiomeDataNeeded() { return false; }
        @Override public boolean isBlockTypeDataNeeded() { return true; }
        @Override public int tileOrdinalX() { return id; }
        @Override public int tileOrdinalY() { return 0; }
        @Override protected String saveTileData() { return Integer.toString(id); }
        @Override public int hashCode() { return id; }
        @Override public boolean equals(Object obj) { return obj instanceof TestTile && ((TestTile) obj).id == id; }
    }

    @Test
    void unregisteredSingleTileWorkStillRuns() {
        TileRenderCoordinator coordinator = new TileRenderCoordinator();
        AtomicInteger executions = new AtomicInteger();

        TileRenderCoordinator.UpdateResult result = coordinator.executeUpdate(new TestTile(0), () -> {
            executions.incrementAndGet();
            return completed();
        });

        assertEquals(1, executions.get());
        assertFalse(result.requeue);
    }

    @Test
    void fullRenderAndQueuedUpdateShareIdenticalAllOutputWork() throws Exception {
        TileRenderCoordinator coordinator = new TileRenderCoordinator();
        TestTile tile = new TestTile(1);
        assertTrue(coordinator.requestUpdate(tile));

        AtomicInteger executions = new AtomicInteger();
        CountDownLatch fullStarted = new CountDownLatch(1);
        CountDownLatch releaseFull = new CountDownLatch(1);
        Thread full = new Thread(() -> coordinator.executeFull(tile, null, () -> {
            executions.incrementAndGet();
            fullStarted.countDown();
            await(releaseFull);
            return completed();
        }));
        full.start();
        assertTrue(fullStarted.await(5, TimeUnit.SECONDS));

        AtomicBoolean updateActionRan = new AtomicBoolean();
        AtomicBoolean requeue = new AtomicBoolean(true);
        Thread update = new Thread(() -> {
            TileRenderCoordinator.UpdateResult result = coordinator.executeUpdate(tile, () -> {
                updateActionRan.set(true);
                executions.incrementAndGet();
                return completed();
            });
            requeue.set(result.requeue);
        });
        update.start();
        releaseFull.countDown();
        full.join(5000);
        update.join(5000);

        assertEquals(1, executions.get());
        assertFalse(updateActionRan.get());
        assertFalse(requeue.get());
    }

    @Test
    void invalidationDuringRunningUpdateRequiresExactlyOneFollowUp() throws Exception {
        TileRenderCoordinator coordinator = new TileRenderCoordinator();
        TestTile tile = new TestTile(2);
        assertTrue(coordinator.requestUpdate(tile));

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicBoolean firstRequeue = new AtomicBoolean();
        Thread first = new Thread(() -> {
            TileRenderCoordinator.UpdateResult result = coordinator.executeUpdate(tile, () -> {
                executions.incrementAndGet();
                firstStarted.countDown();
                await(releaseFirst);
                return completed();
            });
            firstRequeue.set(result.requeue);
        });
        first.start();
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        assertFalse(coordinator.requestUpdate(new TestTile(2)), "running update already owns the queue slot");
        releaseFirst.countDown();
        first.join(5000);
        assertTrue(firstRequeue.get());

        TileRenderCoordinator.UpdateResult second = coordinator.executeUpdate(tile, () -> {
            executions.incrementAndGet();
            return completed();
        });
        assertEquals(2, executions.get());
        assertFalse(second.requeue);
    }

    @Test
    void mapSpecificWorkDoesNotConsumeAllOutputUpdate() throws Exception {
        TileRenderCoordinator coordinator = new TileRenderCoordinator();
        TestTile tile = new TestTile(3);
        assertTrue(coordinator.requestUpdate(tile));

        CountDownLatch specificStarted = new CountDownLatch(1);
        CountDownLatch releaseSpecific = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        Thread specific = new Thread(() -> coordinator.executeFull(tile, "surface", () -> {
            recordExecution(active, maxActive, executions);
            specificStarted.countDown();
            await(releaseSpecific);
            active.decrementAndGet();
            return completed();
        }));
        specific.start();
        assertTrue(specificStarted.await(5, TimeUnit.SECONDS));

        AtomicBoolean updateRequeue = new AtomicBoolean(true);
        Thread update = new Thread(() -> {
            TileRenderCoordinator.UpdateResult result = coordinator.executeUpdate(tile, () -> {
                recordExecution(active, maxActive, executions);
                active.decrementAndGet();
                return completed();
            });
            updateRequeue.set(result.requeue);
        });
        update.start();
        releaseSpecific.countDown();
        specific.join(5000);
        update.join(5000);

        assertEquals(2, executions.get(), "all-output update must follow map-specific work");
        assertEquals(1, maxActive.get(), "older and newer output work must never overlap");
        assertFalse(updateRequeue.get());
    }

    @Test
    void allOutputWorkSatisfiesConcurrentMapSpecificDemand() throws Exception {
        TileRenderCoordinator coordinator = new TileRenderCoordinator();
        TestTile tile = new TestTile(4);
        assertTrue(coordinator.requestUpdate(tile));

        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        Thread update = new Thread(() -> coordinator.executeUpdate(tile, () -> {
            executions.incrementAndGet();
            updateStarted.countDown();
            await(releaseUpdate);
            return completed();
        }));
        update.start();
        assertTrue(updateStarted.await(5, TimeUnit.SECONDS));

        AtomicBoolean specificActionRan = new AtomicBoolean();
        Thread specific = new Thread(() -> coordinator.executeFull(tile, "surface", () -> {
            specificActionRan.set(true);
            executions.incrementAndGet();
            return completed();
        }));
        specific.start();
        waitUntilWaiting(specific);
        releaseUpdate.countDown();
        update.join(5000);
        specific.join(5000);

        assertEquals(1, executions.get());
        assertFalse(specificActionRan.get());
    }

    private static void recordExecution(AtomicInteger active, AtomicInteger maxActive, AtomicInteger executions) {
        executions.incrementAndGet();
        int now = active.incrementAndGet();
        maxActive.accumulateAndGet(now, Math::max);
    }

    private static TileRenderCoordinator.WorkResult completed() {
        return new TileRenderCoordinator.WorkResult(true, true, true, true, 1L, true);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test latch");
            }
        } catch (InterruptedException ix) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ix);
        }
    }

    private static void waitUntilWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING) {
            if (!thread.isAlive() || System.nanoTime() >= deadline) {
                throw new AssertionError("thread did not wait for the running render claim");
            }
            Thread.yield();
        }
    }
}
