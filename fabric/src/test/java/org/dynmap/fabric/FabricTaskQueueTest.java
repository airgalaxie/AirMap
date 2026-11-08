package org.dynmap.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class FabricTaskQueueTest {
    @Test void newSessionAcceptsTasksWithoutRevivingPreviousSession() throws Exception {
        try (FabricTaskQueue old = new FabricTaskQueue(); FabricTaskQueue current = new FabricTaskQueue()) {
            Future<Integer> obsolete = old.submit(() -> fail("previous session"), 0);
            old.close();
            Future<Integer> fresh = current.submit(() -> 7, 0);
            current.run(current.poll());
            assertEquals(7, fresh.get());
            assertThrows(CancellationException.class, obsolete::get);
            assertNull(old.poll());
        }
    }
    @Test void keepsTickDelaysAndSubmissionOrder() throws Exception {
        try (FabricTaskQueue queue = new FabricTaskQueue()) {
            List<Integer> order = new ArrayList<>();
            Future<?> first = queue.submit(() -> order.add(1), 0);
            Future<?> later = queue.submit(() -> order.add(3), 2);
            Future<?> second = queue.submit(() -> order.add(2), 0);
            queue.nextTick();
            for (TaskRecord task; (task = queue.poll()) != null;) queue.run(task);
            assertEquals(List.of(1, 2), order); assertTrue(first.isDone()); assertTrue(second.isDone()); assertFalse(later.isDone());
            queue.nextTick(); queue.run(queue.poll());
            assertEquals(List.of(1, 2, 3), order);
        }
    }

    @Test void shutdownCancelsQueuedAndDispatchedFuturesAndRejectsLateSubmission() throws Exception {
        try (FabricTaskQueue queue = new FabricTaskQueue(); ExecutorService worker = Executors.newFixedThreadPool(2)) {
            Future<Integer> dispatched = queue.submit(() -> fail("must not execute after close"), 0);
            Future<Integer> queued = queue.submit(() -> fail("must not execute after close"), 10);
            TaskRecord polled = queue.poll();
            Future<?> a = worker.submit(() -> assertThrows(CancellationException.class, dispatched::get));
            Future<?> b = worker.submit(() -> assertThrows(CancellationException.class, queued::get));
            queue.close();
            a.get(3, TimeUnit.SECONDS); b.get(3, TimeUnit.SECONDS);
            queue.run(polled);
            assertNull(queue.poll());
            assertThrows(CancellationException.class, () -> queue.submit(() -> 1, 0).get());
        }
    }

    @Test void concurrentShutdownAndSubmissionLeaveNoUnfinishedFutures() throws Exception {
        for (int round = 0; round < 10; round++) {
            try (FabricTaskQueue queue = new FabricTaskQueue(); ExecutorService workers = Executors.newFixedThreadPool(4)) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Future<Integer>>> submitted = new ArrayList<>();
                for (int i = 0; i < 60; i++) submitted.add(workers.submit(() -> { start.await(); return queue.submit(() -> 1, 0); }));
                start.countDown(); queue.close();
                for (Future<Future<Integer>> future : submitted) assertTrue(future.get(3, TimeUnit.SECONDS).isCancelled());
            }
        }
    }

    @Test void closingRunningTaskReleasesFutureWithoutInterruptingItsServerThread() throws Exception {
        try (FabricTaskQueue queue = new FabricTaskQueue(); ExecutorService shutdown = Executors.newSingleThreadExecutor()) {
            CountDownLatch running = new CountDownLatch(1), closed = new CountDownLatch(1);
            Future<Integer> result = queue.submit(() -> {
                running.countDown(); assertTrue(closed.await(3, TimeUnit.SECONDS));
                assertFalse(Thread.currentThread().isInterrupted()); return 1;
            }, 0);
            Future<?> stopped = shutdown.submit(() -> {
                assertTrue(running.await(3, TimeUnit.SECONDS)); queue.close(); closed.countDown(); return null;
            });
            queue.run(queue.poll()); stopped.get(3, TimeUnit.SECONDS);
            assertThrows(CancellationException.class, result::get);
        }
    }

    @Test void exceptionsReachConsumerAndDoNotPreventNextTask() throws Exception {
        try (FabricTaskQueue queue = new FabricTaskQueue()) {
            IllegalStateException cause = new IllegalStateException("server task failed");
            Future<?> failed = queue.submit(() -> { throw cause; }, 0);
            Future<Integer> next = queue.submit(() -> 42, 0);
            queue.run(queue.poll()); queue.run(queue.poll());
            assertSame(cause, assertThrows(ExecutionException.class, failed::get).getCause());
            assertEquals(42, next.get());
        }
    }
}
