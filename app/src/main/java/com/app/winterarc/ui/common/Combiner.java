package com.app.winterarc.ui.common;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.app.winterarc.core.AppExecutors;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Combines several LiveData sources and recomputes a derived value on the IO executor whenever
 * any source changes. Stale computations are dropped, so only the latest result is published.
 * A compute function returning null means "not ready yet" and publishes nothing.
 */
public final class Combiner<T> extends MediatorLiveData<T> {
    private final AppExecutors executors;
    private final Supplier<T> compute;
    private final AtomicInteger generation = new AtomicInteger();

    public Combiner(AppExecutors executors, Supplier<T> compute) {
        this.executors = executors;
        this.compute = compute;
    }

    public <S> void watch(LiveData<S> source, Consumer<S> store) {
        addSource(source, value -> {
            store.accept(value);
            recompute();
        });
    }

    public void recompute() {
        int g = generation.incrementAndGet();
        executors.io().execute(() -> {
            T value = compute.get();
            executors.main().execute(() -> {
                if (g == generation.get() && value != null) setValue(value);
            });
        });
    }
}
