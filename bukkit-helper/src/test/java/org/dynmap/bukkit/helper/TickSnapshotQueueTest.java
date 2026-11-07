package org.dynmap.bukkit.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import org.junit.jupiter.api.Test;

class TickSnapshotQueueTest {
    private final Thread tickThread = Thread.currentThread();

    private <T> TickSnapshotQueue<T> queue() {
        return new TickSnapshotQueue<>(() -> Thread.currentThread() == tickThread, System::nanoTime);
    }

    @Test
    void submissionDoesNotReadLiveDataAndOnlyTickThreadMayCopy() throws Exception {
        TickSnapshotQueue<Integer> queue = queue();
        AtomicInteger copies = new AtomicInteger();
        CompletableFuture<Integer> result = queue.submit(() -> {
            assertSame(tickThread, Thread.currentThread());
            return copies.incrementAndGet();
        });
        assertEquals(0, copies.get());
        assertFalse(result.isDone());
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> assertThrows(IllegalStateException.class,
                    () -> queue.runTick(10, Long.MAX_VALUE))).get(5, TimeUnit.SECONDS);
        }
        queue.runTick(10, Long.MAX_VALUE);
        assertEquals(1, result.join());
    }

    @Test
    void tickThreadCannotWaitEvenForAnAlreadyCompletedSnapshot() {
        TickSnapshotQueue<Integer> queue = queue();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            // This queue identifies the test's executing thread as its owner.
            TickSnapshotQueue<Integer> owned = new TickSnapshotQueue<>(() -> true, System::nanoTime);
            assertThrows(IllegalStateException.class, () -> owned.await(new CompletableFuture<>()));
            assertThrows(IllegalStateException.class, () -> owned.await(CompletableFuture.completedFuture(1)));
        });
        assertThrows(IllegalStateException.class, queue::requireWorkerThread);
    }

    @Test
    void encodingRunsOnConsumingWorkerAfterCopyHasFinished() throws Exception {
        TickSnapshotQueue<Integer> queue = queue();
        AtomicInteger encoded = new AtomicInteger();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Supplier<String> supplier = worker.submit(() -> queue.request(() -> {
                assertSame(tickThread, Thread.currentThread());
                return 42;
            }, snapshot -> {
                assertNotSame(tickThread, Thread.currentThread());
                encoded.incrementAndGet();
                return "nbt:" + snapshot;
            })).get(5, TimeUnit.SECONDS);
            assertEquals(0, encoded.get());
            queue.runTick(10, Long.MAX_VALUE);
            assertEquals(0, encoded.get(), "Completing the snapshot must not encode on the tick thread");
            assertThrows(IllegalStateException.class, supplier::get);
            assertEquals("nbt:42", worker.submit(supplier::get).get(5, TimeUnit.SECONDS));
            assertEquals(1, encoded.get());
        }
    }

    @Test
    void unprotectedFastutilIterationReproducesTheReportedFailure() {
        Map<Integer, Integer> blockEntities = new Object2ObjectOpenHashMap<>();
        blockEntities.put(1, 1);
        var iterator = blockEntities.keySet().iterator();
        blockEntities.clear(); // Deterministically place a mutation between iterator creation and next().
        NullPointerException failure = assertThrows(NullPointerException.class, iterator::next);
        assertEquals("it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap$MapIterator",
                failure.getStackTrace()[0].getClassName());
    }

    @Test
    void globalChunkLimitDefersRemainingRequestsInOrder() {
        TickSnapshotQueue<Integer> queue = queue();
        AtomicInteger copies = new AtomicInteger();
        List<CompletableFuture<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 7; i++) results.add(queue.submit(copies::incrementAndGet));
        queue.runTick(3, Long.MAX_VALUE);
        assertEquals(3, copies.get());
        assertFalse(results.get(3).isDone());
        queue.runTick(3, Long.MAX_VALUE);
        assertEquals(6, copies.get());
        queue.runTick(3, Long.MAX_VALUE);
        for (int i = 0; i < 7; i++) assertEquals(i + 1, results.get(i).join());
    }

    @Test
    void timeBudgetStopsBeforeStartingAnotherCopy() {
        AtomicLong clock = new AtomicLong();
        TickSnapshotQueue<Integer> queue = new TickSnapshotQueue<>(() -> true, clock::get);
        CompletableFuture<Integer> slow = queue.submit(() -> {
            clock.addAndGet(2_000_000);
            return 1;
        });
        CompletableFuture<Integer> next = queue.submit(() -> 2);
        queue.runTick(100, 2_000_000);
        assertEquals(1, slow.join());
        assertFalse(next.isDone());
        queue.runTick(100, 2_000_000);
        assertEquals(2, next.join());
    }

    @Test
    void unloadedChunkIsResolvedAtExecutionTimeAndReturnsNull() {
        TickSnapshotQueue<Map<Integer, Integer>> queue = queue();
        AtomicReference<Map<Integer, Integer>> loaded = new AtomicReference<>(Map.of(1, 2));
        CompletableFuture<Map<Integer, Integer>> snapshot = queue.submit(() -> {
            Map<Integer, Integer> live = loaded.get();
            return live == null ? null : new HashMap<>(live);
        });
        loaded.set(null);
        queue.runTick(10, Long.MAX_VALUE);
        assertNull(snapshot.join()); // Allows the unchanged core to fall back to region I/O.
    }

    @Test
    void unloadedChunksDoNotSpendTheLoadedSnapshotQuota() {
        TickSnapshotQueue<Integer> queue = queue();
        List<CompletableFuture<Integer>> unloaded = new ArrayList<>();
        for (int i = 0; i < 100; i++) unloaded.add(queue.submit(() -> null));
        CompletableFuture<Integer> loaded = queue.submit(() -> 1);
        CompletableFuture<Integer> deferred = queue.submit(() -> 2);
        queue.runTick(1, Long.MAX_VALUE);
        unloaded.forEach(result -> assertNull(result.join()));
        assertEquals(1, loaded.join());
        assertFalse(deferred.isDone());
    }

    @Test
    void aNewProviderDoesNotReviveRequestsFromBeforeReload() {
        TickSnapshotQueue<Integer> previous = queue();
        CompletableFuture<Integer> obsolete = previous.submit(() -> fail("Old world request survived reload"));
        previous.close();
        TickSnapshotQueue<Integer> current = queue();
        CompletableFuture<Integer> fresh = current.submit(() -> 42);
        current.runTick(1, Long.MAX_VALUE);
        previous.runTick(1, Long.MAX_VALUE);
        assertThrows(CancellationException.class, obsolete::join);
        assertEquals(42, fresh.join());
    }

    @Test
    void failedCopyDoesNotStrandItsWaiterOrOtherRequests() throws Exception {
        TickSnapshotQueue<Integer> queue = queue();
        IllegalStateException failure = new IllegalStateException("copy failed");
        CompletableFuture<Integer> failed = queue.submit(() -> { throw failure; });
        CompletableFuture<Integer> next = queue.submit(() -> 2);
        queue.runTick(10, Long.MAX_VALUE);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> assertSame(failure,
                    assertThrows(CompletionException.class, () -> queue.await(failed)).getCause()))
                    .get(5, TimeUnit.SECONDS);
        }
        assertEquals(2, next.join());
    }

    @Test
    void shutdownReleasesWaitingWorkerBeforeTickThreadJoinsIt() throws Exception {
        TickSnapshotQueue<Integer> queue = queue();
        CompletableFuture<Integer> pending = queue.submit(() -> fail("Must not copy after shutdown"));
        CountDownLatch waiting = new CountDownLatch(1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> render = worker.submit(() -> {
                waiting.countDown();
                assertThrows(CancellationException.class, () -> queue.await(pending));
            });
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            queue.close();
            render.get(5, TimeUnit.SECONDS);
        }
        assertThrows(CancellationException.class, () -> queue.submit(() -> 1).join());
        queue.runTick(10, Long.MAX_VALUE);
    }

    @Test
    void interruptionCancelsCopyAndPreservesInterruptStatus() throws Exception {
        TickSnapshotQueue<Integer> queue = queue();
        CompletableFuture<Integer> pending = queue.submit(() -> fail("Cancelled work must not read the world"));
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    assertThrows(CancellationException.class, () -> queue.await(pending));
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            }).get(5, TimeUnit.SECONDS);
        }
        assertTrue(pending.isCancelled());
        CompletableFuture<Integer> next = queue.submit(() -> 2);
        queue.runTick(1, Long.MAX_VALUE);
        assertEquals(2, next.join());
    }

    @Test
    void concurrentRenderRequestsSeeConsistentDetachedBlockEntitySnapshots() throws Exception {
        // The server owns this deliberately non-concurrent live map. Render
        // workers continuously request/consume snapshots as ticks add/remove
        // chunks and BlockEntities, including in-place BlockEntity changes.
        TickSnapshotQueue<Map<Integer, int[]>> queue = queue();
        Map<Integer, int[]> live = new Object2ObjectOpenHashMap<>();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger copied = new AtomicInteger();
        AtomicInteger generation = new AtomicInteger();
        List<Future<?>> renders = new ArrayList<>();
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            for (int worker = 0; worker < 4; worker++) {
                renders.add(workers.submit(() -> {
                    for (int i = 0; i < 250; i++) {
                        CompletableFuture<Map<Integer, int[]>> request = queue.submit(() -> {
                            assertSame(tickThread, Thread.currentThread());
                            Map<Integer, int[]> snapshot = new HashMap<>();
                            live.forEach((key, value) -> snapshot.put(key, value.clone()));
                            copied.incrementAndGet();
                            return snapshot;
                        });
                        submitted.incrementAndGet();
                        Map<Integer, int[]> snapshot = queue.await(request);
                        assertNotSame(tickThread, Thread.currentThread());
                        // Simulate asynchronous encoding after subsequent ticks.
                        int version = snapshot.isEmpty() ? -1 : snapshot.values().iterator().next()[0];
                        Thread.yield();
                        snapshot.forEach((key, value) -> assertEquals(version, value[0]));
                        consumed.incrementAndGet();
                    }
                }));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try {
                while (consumed.get() < 1000 && System.nanoTime() < deadline) {
                    int tick = generation.incrementAndGet();
                    if (tick % 7 == 0) live.clear(); // Chunk unload/reload.
                    live.put(tick % 128, new int[] {tick});
                    live.remove((tick + 37) % 128);
                    live.values().forEach(value -> value[0] = tick);
                    queue.runTick(3, 2_000_000);
                    Thread.yield();
                }
            } finally {
                queue.close();
            }
            for (Future<?> render : renders) render.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1000, submitted.get());
        assertEquals(1000, copied.get());
        assertEquals(1000, consumed.get());
    }
}
