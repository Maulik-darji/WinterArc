package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.shown;
import static com.app.winterarc.ui.Waits.waitForDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The demo story end to end: first launch → onboarding → create a consistency goal → check in →
 * the square fills and the dashboard shows the day as done. Runs with clean app data.
 */
@RunWith(AndroidJUnit4.class)
public class CoreJourneyTest {

    @Test
    public void onboardingCreateGoalAndCompleteToday() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            // Onboarding
            waitForDisplayed(withText(R.string.onboarding_1_title));
            onView(withId(R.id.next)).perform(click());
            onView(withId(R.id.next)).perform(click());
            onView(shown(withText(R.string.onboarding_get_started))).perform(click());

            // Sign up with mobile number + OTP
            AuthSteps.signUp();

            // Empty dashboard
            waitForDisplayed(withText(R.string.home_empty_title));
            onView(withId(R.id.empty_cta)).perform(click());

            // Wizard: activity → mode → target → details → schedule → review
            waitForDisplayed(withText(R.string.editor_step_activity));
            onView(shown(withText(R.string.activity_walking))).perform(click());
            waitForDisplayed(withText(R.string.editor_step_mode));
            onView(shown(withText(R.string.mode_consistency))).perform(click());
            waitForDisplayed(withText(R.string.editor_step_target_consistency));
            onView(withId(R.id.next)).perform(click());
            waitForDisplayed(withText(R.string.editor_step_details));
            onView(withId(R.id.next)).perform(click()); // title was suggested from the activity
            waitForDisplayed(withText(R.string.editor_step_schedule));
            onView(withId(R.id.next)).perform(click());
            waitForDisplayed(withText(R.string.editor_step_review));
            onView(shown(withText(R.string.editor_create))).perform(click());

            // Goal detail: complete today's target
            waitForDisplayed(withText(containsString("Did you complete today")));
            onView(withId(R.id.btn_complete)).perform(click());
            waitForDisplayed(withText(R.string.detail_done_title));
            onView(withId(R.id.btn_undo)).check(matches(isDisplayed()));

            // Undo, then complete again (undo of an accidental completion)
            onView(withId(R.id.btn_undo)).perform(click());
            waitForDisplayed(allOf(withId(R.id.btn_complete), isDisplayed()));
            onView(withId(R.id.btn_complete)).perform(click());
            waitForDisplayed(withText(R.string.detail_done_title));

            // Back on the dashboard everything for today is done
            pressBack();
            waitForDisplayed(withText(R.string.home_summary_all_done));
            waitForDisplayed(withText(R.string.card_completed)); // polls past the item change animation
        }
    }

    @Test
    public void skipOnboardingShowsDashboardAndSettings() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitForDisplayed(withId(R.id.skip));
            onView(withId(R.id.skip)).perform(click());
            AuthSteps.signUp();
            waitForDisplayed(withText(R.string.home_empty_title));
            onView(withId(R.id.action_settings)).perform(click());
            waitForDisplayed(withText(containsString("Signed in as +919876543210")));
            waitForDisplayed(withText(R.string.settings_privacy));
            onView(shown(withText(R.string.settings_privacy))).perform(click());
            waitForDisplayed(withText(containsString("work entirely on your device")));
        }
    }
}
