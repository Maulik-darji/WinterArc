package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

import android.os.SystemClock;
import android.view.View;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

/** Polls for a view: database work runs on a background executor Espresso doesn't track. */
public final class Waits {
    private Waits() {}

    /** Matches only the on-screen instance (hidden wizard steps contain the same texts). */
    public static Matcher<View> shown(Matcher<View> matcher) {
        return Matchers.allOf(matcher, isDisplayed());
    }

    public static void waitForDisplayed(Matcher<View> matcher) {
        long deadline = SystemClock.uptimeMillis() + 8_000;
        Throwable last = null;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(shown(matcher)).check(matches(isDisplayed()));
                return;
            } catch (Throwable t) {
                last = t;
                SystemClock.sleep(150);
            }
        }
        throw new AssertionError("View never displayed: " + matcher, last);
    }
}
