package org.dynmap.fabric;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Owns both scheduled and dispatched server tasks until completion or shutdown. */
final class FabricTaskQueue implements AutoCloseable {
    private final PriorityQueue<TaskRecord> tasks = new PriorityQueue<>();
    private final Set<TaskRecord> dispatched = new HashSet<>();
    private long tick;
    private long nextId;
    private boolean closed;

    synchronized <T> FutureTask<T> submit(Callable<T> action, long delay) {
        FutureTask<T> future = new FutureTask<>(action);
        if (closed) future.cancel(false);
        else tasks.add(new TaskRecord(tick + delay, nextId++, future));
        return future;
    }

    synchronized void nextTick() { tick++; }

    synchronized TaskRecord poll() {
        if (closed || tasks.isEmpty() || tasks.peek().getTickToRun() > tick) return null;
        TaskRecord task = tasks.poll();
        dispatched.add(task);
        return task;
    }

    void run(TaskRecord task) {
        try { task.run(); }
        finally { synchronized (this) { dispatched.remove(task); } }
    }

    @Override public synchronized void close() {
        closed = true;
        for (TaskRecord task : tasks) task.cancel();
        for (TaskRecord task : dispatched) task.cancel();
        tasks.clear();
        dispatched.clear();
    }
}
