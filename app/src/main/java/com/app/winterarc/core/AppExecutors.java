package com.app.winterarc.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Threading for the Java stack. All database work runs on {@link #io()}; results come back on
 * {@link #main()}. A single IO thread serialises writes, which keeps check-in ordering simple.
 */
public class AppExecutors {
    private final Executor io;
    private final Executor main;
    private final Executor network;

    public AppExecutors(Executor io, Executor main) {
        this(io, main, io);
    }

    public AppExecutors(Executor io, Executor main, Executor network) {
        this.io = io;
        this.main = main;
        this.network = network;
    }

    public static AppExecutors create() {
        ExecutorService io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "winterarc-io");
            t.setDaemon(true);
            return t;
        });
        Handler handler = new Handler(Looper.getMainLooper());
        // Cloud calls wait on the network, so they get their own threads and never hold up the
        // single database thread (check-ins stay instant even on a slow connection).
        ExecutorService network = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "winterarc-net");
            t.setDaemon(true);
            return t;
        });
        return new AppExecutors(io, handler::post, network);
    }

    /** Runs everything inline; for unit tests. */
    public static AppExecutors direct() {
        Executor direct = Runnable::run;
        return new AppExecutors(direct, direct);
    }

    public Executor io() {
        return io;
    }

    public Executor main() {
        return main;
    }

    /** For work that waits on the network (cloud reads and writes). */
    public Executor network() {
        return network;
    }
}
