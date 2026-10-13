package org.dynmap.fabric.event;

import java.util.Objects;
import java.util.function.Function;

/**
 * Minimal array-backed callback event owned by the AirMap Fabric adapter.
 */
public final class SimpleEvent<T> {
    private final Class<T> listenerType;
    private final Function<T[], T> invokerFactory;
    private volatile T[] listeners;
    private volatile T invoker;

    @SuppressWarnings("unchecked")
    public SimpleEvent(Class<T> listenerType, Function<T[], T> invokerFactory) {
        this.listenerType = Objects.requireNonNull(listenerType, "listenerType");
        this.invokerFactory = Objects.requireNonNull(invokerFactory, "invokerFactory");
        this.listeners = (T[]) java.lang.reflect.Array.newInstance(listenerType, 0);
        this.invoker = invokerFactory.apply(listeners);
    }

    public synchronized void register(T listener) {
        Objects.requireNonNull(listener, "listener");
        T[] updated = java.util.Arrays.copyOf(listeners, listeners.length + 1);
        updated[listeners.length] = listener;
        listeners = updated;
        invoker = invokerFactory.apply(updated);
    }

    public T invoker() {
        return invoker;
    }
}
