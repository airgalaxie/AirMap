package org.dynmap.bukkit.helper;

import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Transfers live-world reads to the tick thread; completion never processes NBT. */
final class TickSnapshotQueue<T> implements AutoCloseable {
    private record Request<T>(Supplier<T> copy, CompletableFuture<T> result) { }

    private final ArrayDeque<Request<T>> requests = new ArrayDeque<>();
    private final BooleanSupplier tickThread;
    private final LongSupplier nanoTime;
    private boolean closed;

    TickSnapshotQueue(BooleanSupplier tickThread, LongSupplier nanoTime) {
        this.tickThread = tickThread;
        this.nanoTime = nanoTime;
    }

    synchronized CompletableFuture<T> submit(Supplier<T> copy) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (closed) {
            result.completeExceptionally(new CancellationException("Chunk snapshot provider stopped"));
        } else {
            requests.addLast(new Request<>(copy, result));
        }
        return result;
    }

    <R> Supplier<R> request(Supplier<T> copy, Function<T, R> encode) {
        requireWorkerThread();
        CompletableFuture<T> result = submit(copy);
        return () -> encode.apply(await(result));
    }

    void requireWorkerThread() {
        if (tickThread.getAsBoolean()) {
            throw new IllegalStateException("Chunk snapshot results must be consumed off the Paper tick thread");
        }
    }

    T await(CompletableFuture<T> result) {
        requireWorkerThread();
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            result.cancel(false);
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for a chunk snapshot");
        } catch (ExecutionException exception) {
            throw new CompletionException("Cannot copy loaded chunk", exception.getCause());
        }
    }

    void runTick(int limit, long budgetNanos) {
        if (!tickThread.getAsBoolean()) {
            throw new IllegalStateException("Chunk snapshots require the Paper tick thread");
        }
        long start = nanoTime.getAsLong();
        for (int processed = 0; processed < limit && nanoTime.getAsLong() - start < budgetNanos;) {
            Request<T> request;
            synchronized (this) {
                request = requests.pollFirst();
            }
            if (request == null) return;
            if (request.result().isDone()) continue;
            try {
                T snapshot = request.copy().get();
                // Missing chunks only need a cheap loaded-state lookup. Keep
                // them within the time budget without spending the copy quota.
                if (snapshot != null) processed++;
                request.result().complete(snapshot);
            } catch (Throwable exception) {
                processed++;
                request.result().completeExceptionally(exception);
                if (exception instanceof Error error) throw error;
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        Request<T> request;
        while ((request = requests.pollFirst()) != null) {
            request.result().completeExceptionally(new CancellationException("Chunk snapshot provider stopped"));
        }
    }
}
