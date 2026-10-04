package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static com.app.winterarc.ui.Waits.shown;
import static com.app.winterarc.ui.Waits.waitForDisplayed;
import static org.hamcrest.Matchers.allOf;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Long running milestones show a gentle, non-blocking safety notice in the wizard. */
@RunWith(AndroidJUnit4.class)
public class SafetyConfirmationTest {

    @Test
    public void marathonMilestoneShowsSafetyNotice() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitForDisplayed(withId(R.id.skip));
            onView(withId(R.id.skip)).perform(click());
            AuthSteps.signUp();
            waitForDisplayed(withId(R.id.empty_cta));
            onView(withId(R.id.empty_cta)).perform(click());
            waitForDisplayed(withText(R.string.activity_running));
            onView(shown(withText(R.string.activity_running))).perform(click());
            waitForDisplayed(withText(R.string.mode_progression));
            onView(shown(withText(R.string.mode_progression))).perform(click());
            waitForDisplayed(withText(R.string.editor_step_target_progression));
            // Choose the 42.2 km milestone from the carousel by its spoken label.
            onView(withId(R.id.milestones)).perform(
                    androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition(9));
            onView(shown(withText("42.2"))).perform(click());
            waitForDisplayed(withText(R.string.milestone_msg_42));
            // The notice sits below the fold of the target step: check it is visible, not on screen.
            onView(allOf(withId(R.id.safety_title), isDescendantOfA(withId(R.id.step_target))))
                    .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        }
    }
}
