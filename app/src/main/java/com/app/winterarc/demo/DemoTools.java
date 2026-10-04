package com.app.winterarc.demo;

import androidx.annotation.WorkerThread;

/**
 * Presentation tooling. The debug source set provides a real implementation; release builds bind
 * {@link #UNAVAILABLE}, so no demo code or data ships to users.
 */
public interface DemoTools {
    boolean isAvailable();

    /** Replaces all goals with a realistic multi-month demo scenario. */
    @WorkerThread
    void loadDemoScenario();

    /** Deletes every goal and its history. */
    @WorkerThread
    void clearAllData();

    DemoTools UNAVAILABLE = new DemoTools() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void loadDemoScenario() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearAllData() {
            throw new UnsupportedOperationException();
        }
    };
}
