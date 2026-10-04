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

    public AppExecutors(Executor io, Executor main) {
        this.io = io;
        this.main = main;
    }

    public static AppExecutors create() {
        ExecutorService io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "winterarc-io");
            t.setDaemon(true);
            return t;
        });
        Handler handler = new Handler(Looper.getMainLooper());
        return new AppExecutors(io, handler::post);
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
}
