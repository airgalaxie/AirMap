package org.dynmap.fabric;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Server-thread copies; encoding, parsing and interruptible I/O waits belong to workers. */
final class FabricChunkProvider<T> implements AutoCloseable {
    private record Request<T>(Supplier<T> copy, CompletableFuture<T> result) { }
    private final ArrayDeque<Request<T>> snapshots = new ArrayDeque<>();
    private final Set<CompletableFuture<T>> pendingCopies = new HashSet<>();
    private final Set<CompletableFuture<?>> reads = new HashSet<>();
    private final BooleanSupplier serverThread;
    private final LongSupplier nanoTime;
    private boolean closed;

    FabricChunkProvider(BooleanSupplier serverThread, LongSupplier nanoTime) {
        this.serverThread = serverThread;
        this.nanoTime = nanoTime;
    }

    void requireWorker() {
        if (serverThread.getAsBoolean()) {
            throw new IllegalStateException("Chunk results must be consumed off the Fabric server thread");
        }
    }

    static void requireCurrentWorld(BooleanSupplier current) {
        if (!current.getAsBoolean()) throw new CancellationException("Chunk world or provider changed");
    }

    <R> Supplier<R> request(BooleanSupplier current, Supplier<T> copy, Function<T, R> encode) {
        requireWorker();
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed) result.cancel(false);
            else {
                pendingCopies.add(result);
                result.whenComplete((value, failure) -> {
                    synchronized (this) { pendingCopies.remove(result); }
                });
                snapshots.addLast(new Request<>(() -> {
                    requireCurrentWorld(current);
                    return copy.get();
                }, result));
            }
        }
        return () -> {
            T snapshot = await(result);
            requireCurrentWorld(current);
            R value = encode.apply(snapshot);
            requireCurrentWorld(current);
            return value;
        };
    }

    synchronized <R> CompletableFuture<R> read(BooleanSupplier current, Supplier<CompletableFuture<R>> start) {
        requireWorker();
        if (closed) throw new CancellationException("Fabric chunk provider stopped");
        requireCurrentWorld(current);
        CompletableFuture<R> source = start.get();
        // Cancel only our consumer: Minecraft owns the underlying I/O operation.
        CompletableFuture<R> result = new CompletableFuture<>();
        reads.add(result);
        result.whenComplete((value, failure) -> {
            synchronized (this) { reads.remove(result); }
        });
        source.whenComplete((value, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else result.complete(value);
        });
        return result;
    }

    <R> R await(Future<R> result) {
        requireWorker();
        try {
            return result.get();
        } catch (InterruptedException failure) {
            result.cancel(false);
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted waiting for Fabric chunk data");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new CompletionException("Cannot read Fabric chunk data", cause);
        }
    }

    void processTick(int chunkLimit, long budgetNanos) {
        if (!serverThread.getAsBoolean()) throw new IllegalStateException("Chunk copies require the Fabric server thread");
        long start = nanoTime.getAsLong();
        for (int copied = 0; copied < chunkLimit && nanoTime.getAsLong() - start < budgetNanos;) {
            Request<T> request;
            synchronized (this) { request = snapshots.pollFirst(); }
            if (request == null) return;
            if (request.result().isDone()) continue;
            try {
                T snapshot = request.copy().get();
                if (snapshot != null) copied++;
                request.result().complete(snapshot);
            } catch (Throwable failure) {
                copied++;
                request.result().completeExceptionally(failure);
                if (failure instanceof Error error) throw error;
            }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        Request<T> request;
        while ((request = snapshots.pollFirst()) != null) request.result().cancel(false);
        for (CompletableFuture<T> copy : Set.copyOf(pendingCopies)) copy.cancel(false);
        pendingCopies.clear();
        // Completion callbacks remove entries; iterate a detached copy.
        for (CompletableFuture<?> read : Set.copyOf(reads)) read.cancel(false);
        reads.clear();
    }
}
