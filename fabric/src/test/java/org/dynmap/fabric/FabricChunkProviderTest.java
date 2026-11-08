package org.dynmap.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FabricChunkProviderTest {
    private final Thread owner = Thread.currentThread();
    private <T> FabricChunkProvider<T> provider() {
        return new FabricChunkProvider<>(() -> Thread.currentThread() == owner, System::nanoTime);
    }

    @Test void copiesOnServerThreadAndEncodesAndParsesOnlyOnWorker() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            AtomicInteger encoded = new AtomicInteger();
            Supplier<String> result = worker.submit(() -> provider.request(() -> true, () -> {
                assertSame(owner, Thread.currentThread()); return 42;
            }, data -> {
                assertNotSame(owner, Thread.currentThread()); encoded.incrementAndGet(); return "nbt:" + data;
            })).get(3, TimeUnit.SECONDS);
            assertEquals(0, encoded.get());
            provider.processTick(1, Long.MAX_VALUE);
            assertEquals(0, encoded.get());
            assertThrows(IllegalStateException.class, result::get);
            assertEquals("nbt:42", worker.submit(result::get).get(3, TimeUnit.SECONDS));
        }
    }

    @Test void dynamicallyChangedGlobalQuotaAppliesAcrossRenderRequests() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            AtomicInteger copied = new AtomicInteger();
            List<Supplier<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) results.add(worker.submit(() -> provider.request(() -> true, copied::incrementAndGet, x -> x)).get());
            provider.processTick(1, Long.MAX_VALUE); assertEquals(1, copied.get());
            provider.processTick(3, Long.MAX_VALUE); assertEquals(4, copied.get());
            provider.processTick(2, Long.MAX_VALUE); assertEquals(6, copied.get());
            for (int i = 0; i < results.size(); i++) assertEquals(i + 1, worker.submit(results.get(i)::get).get());
        }
    }

    @Test void timeBudgetStopsBeforeStartingNextCopy() throws Exception {
        AtomicLong clock = new AtomicLong();
        try (FabricChunkProvider<Integer> provider = new FabricChunkProvider<>(() -> Thread.currentThread() == owner, clock::get);
             ExecutorService worker = Executors.newSingleThreadExecutor()) {
            AtomicInteger copied = new AtomicInteger();
            for (int i = 0; i < 3; i++) worker.submit(() -> provider.request(() -> true,
                    () -> { clock.addAndGet(2_000_000); return copied.incrementAndGet(); }, x -> x)).get();
            provider.processTick(50, 2_000_000); assertEquals(1, copied.get());
            provider.processTick(50, 4_000_000); assertEquals(3, copied.get());
        }
    }

    @Test void absentChunksDoNotSpendTheLoadedChunkQuota() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < 20; i++) worker.submit(() -> provider.request(() -> true, () -> null, x -> x)).get();
            AtomicInteger copied = new AtomicInteger();
            worker.submit(() -> provider.request(() -> true, copied::incrementAndGet, x -> x)).get();
            provider.processTick(1, Long.MAX_VALUE);
            assertEquals(1, copied.get());
        }
    }

    @Test void copyAndEncodingFailuresReachWorkerAndDoNotStrandOtherRequests() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            IllegalStateException copyFailure = new IllegalStateException("copy failed");
            IllegalArgumentException parseFailure = new IllegalArgumentException("parse failed");
            Supplier<Integer> failedCopy = worker.submit(() -> provider.request(() -> true, () -> { throw copyFailure; }, x -> x)).get();
            Supplier<Integer> failedParse = worker.submit(() -> provider.<Integer>request(() -> true, () -> 1, x -> { throw parseFailure; })).get();
            Supplier<Integer> next = worker.submit(() -> provider.request(() -> true, () -> 7, x -> x)).get();
            provider.processTick(10, Long.MAX_VALUE);
            worker.submit(() -> {
                assertSame(copyFailure, assertThrows(IllegalStateException.class, failedCopy::get));
                assertSame(parseFailure, assertThrows(IllegalArgumentException.class, failedParse::get));
                assertEquals(7, next.get());
            }).get(3, TimeUnit.SECONDS);
        }
    }

    @Test void obsoleteWorldIsRejectedBeforeCopyAndAfterEncoding() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            AtomicBoolean current = new AtomicBoolean(true);
            Supplier<Integer> obsolete = worker.submit(() -> provider.request(current::get,
                    () -> fail("must not read an unloaded or replaced world"), x -> x)).get();
            current.set(false); provider.processTick(1, Long.MAX_VALUE);
            worker.submit(() -> assertThrows(CancellationException.class, obsolete::get)).get();
            current.set(true);
            Supplier<Integer> changing = worker.submit(() -> provider.request(current::get, () -> 1,
                    x -> { current.set(false); return x; })).get();
            provider.processTick(1, Long.MAX_VALUE);
            worker.submit(() -> assertThrows(CancellationException.class, changing::get)).get();
        }
    }

    @Test void shutdownReleasesSnapshotAndIoWaitersWithoutAnotherTick() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newFixedThreadPool(2)) {
            Supplier<Integer> snapshot = worker.submit(() -> provider.request(() -> true, () -> fail("copy after shutdown"), x -> x)).get();
            CompletableFuture<Integer> engineIo = new CompletableFuture<>();
            CompletableFuture<Integer> io = worker.submit(() -> provider.read(() -> true, () -> engineIo)).get();
            Future<?> first = worker.submit(() -> assertThrows(CancellationException.class, snapshot::get));
            Future<?> second = worker.submit(() -> assertThrows(CancellationException.class, () -> provider.await(io)));
            provider.close();
            first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS);
            assertFalse(engineIo.isCancelled(), "Minecraft owns I/O completion; only our wait is cancelled");
            worker.submit(() -> assertThrows(CancellationException.class, () -> provider.read(() -> true, CompletableFuture::new))).get();
            provider.processTick(100, Long.MAX_VALUE);
        }
    }

    @Test void interruptedIoWaitPreservesInterruptAndCannotBecomeAnAbsentChunk() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                CompletableFuture<Optional<Integer>> read = provider.read(() -> true, CompletableFuture::new);
                Thread.currentThread().interrupt();
                try {
                    assertThrows(CancellationException.class, () -> provider.await(read));
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally { Thread.interrupted(); }
            }).get(3, TimeUnit.SECONDS);
        }
    }

    @Test void shutdownAlsoReleasesCopyAlreadyDispatchedWithoutInterruptingServerThread() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            CountDownLatch copying = new CountDownLatch(1), closed = new CountDownLatch(1);
            Supplier<Integer> result = worker.submit(() -> provider.request(() -> true, () -> {
                copying.countDown();
                try { assertTrue(closed.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                assertFalse(Thread.currentThread().isInterrupted());
                return 1;
            }, x -> x)).get();
            Future<?> shutdown = worker.submit(() -> {
                assertTrue(copying.await(3, TimeUnit.SECONDS));
                provider.close(); closed.countDown(); return null;
            });
            provider.processTick(1, Long.MAX_VALUE); shutdown.get(3, TimeUnit.SECONDS);
            worker.submit(() -> assertThrows(CancellationException.class, result::get)).get();
        }
    }

    @Test void ioFailureIsDifferentFromSuccessfullyMissingData() throws Exception {
        try (FabricChunkProvider<Integer> provider = provider(); ExecutorService worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                IOException failure = new IOException("region read failed");
                var failed = provider.read(() -> true, () -> CompletableFuture.failedFuture(failure));
                assertSame(failure, assertThrows(CompletionException.class, () -> provider.await(failed)).getCause());
                var missing = provider.read(() -> true, () -> CompletableFuture.completedFuture(Optional.empty()));
                assertTrue(provider.await(missing).isEmpty());
                assertThrows(CancellationException.class, () -> provider.read(() -> false,
                        () -> fail("must not access an obsolete chunk provider")));
            }).get(3, TimeUnit.SECONDS);
        }
    }

    @Test void aNewProviderCannotReviveWorkFromPreviousServerSession() throws Exception {
        try (FabricChunkProvider<Integer> old = provider(); FabricChunkProvider<Integer> fresh = provider();
             ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Supplier<Integer> stale = worker.submit(() -> old.request(() -> true, () -> fail("old server world"), x -> x)).get();
            old.close();
            Supplier<Integer> current = worker.submit(() -> fresh.request(() -> true, () -> 9, x -> x)).get();
            fresh.processTick(1, Long.MAX_VALUE); old.processTick(1, Long.MAX_VALUE);
            worker.submit(() -> { assertThrows(CancellationException.class, stale::get); assertEquals(9, current.get()); }).get();
        }
    }
}
