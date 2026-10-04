package com.app.winterarc.core.analytics;

import androidx.annotation.Nullable;

/** Crash/error reporting seam. Release builds use a no-op until a provider is configured. */
public interface CrashReporter {
    void recordNonFatal(Throwable throwable, @Nullable String context);

    void log(String message);

    CrashReporter NO_OP = new CrashReporter() {
        @Override
        public void recordNonFatal(Throwable throwable, @Nullable String context) { }

        @Override
        public void log(String message) { }
    };
}
