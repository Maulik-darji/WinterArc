package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.shown;
import static com.app.winterarc.ui.Waits.waitForDisplayed;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Mobile-number sign-up with OTP, using the debug fake provider (code 123456). */
@RunWith(AndroidJUnit4.class)
public class SignupFlowTest {

    @Test
    public void validationWrongCodeSuccessAndSignOut() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitForDisplayed(withId(R.id.skip));
            onView(withId(R.id.skip)).perform(click());
            waitForDisplayed(withText(R.string.signup_title));
            AuthSteps.selectIndia();

            // Too-short number is rejected locally.
            onView(withId(R.id.phone)).perform(replaceText("98765"));
            onView(withId(R.id.send)).perform(click());
            waitForDisplayed(withText(containsString("too short")));

            // Valid number → code screen with the number masked.
            onView(withId(R.id.phone)).perform(replaceText(AuthSteps.TEST_NUMBER));
            onView(withId(R.id.send)).perform(click());
            waitForDisplayed(withText(R.string.otp_title));
            waitForDisplayed(withText(containsString("+91 ••••••3210")));

            // Wrong code → calm error, stays on the screen.
            onView(withId(R.id.code)).perform(replaceText("000000"));
            waitForDisplayed(withText(R.string.auth_error_invalid_code));

            // Right code → dashboard.
            onView(withId(R.id.code)).perform(replaceText(AuthSteps.TEST_CODE));
            waitForDisplayed(withText(R.string.home_empty_title));

            // Sign out from Settings → back to sign-up.
            onView(withId(R.id.action_settings)).perform(click());
            waitForDisplayed(withText(R.string.settings_sign_out));
            onView(shown(withText(R.string.settings_sign_out))).perform(click());
            onView(withId(android.R.id.button1)).perform(click());
            waitForDisplayed(withText(R.string.signup_title));
        }
    }
}
