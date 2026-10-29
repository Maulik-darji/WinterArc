package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.shown;
import static com.app.winterarc.ui.Waits.waitForDisplayed;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Email sign-up, validation, sign out and log back in, via the debug local test provider. */
@RunWith(AndroidJUnit4.class)
public class SignupFlowTest {

    @Test
    public void validationSignUpSignOutAndLogIn() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitForDisplayed(withId(R.id.skip));
            onView(withId(R.id.skip)).perform(click());
            AuthSteps.openSignUp();

            // Mismatched passwords are caught before anything is sent.
            AuthSteps.fillSignUp(AuthSteps.TEST_NAME, AuthSteps.TEST_EMAIL, AuthSteps.TEST_PASSWORD, "winter2027");
            waitForDisplayed(withText(R.string.auth_problem_passwords_differ));

            // Matching passwords → account created → dashboard.
            onView(withId(R.id.confirm)).perform(scrollTo(), replaceText(AuthSteps.TEST_PASSWORD), closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(), click());
            waitForDisplayed(withText(R.string.home_empty_title));

            // Sign out from Settings → welcome.
            onView(withId(R.id.action_settings)).perform(click());
            waitForDisplayed(withText(R.string.settings_sign_out));
            onView(shown(withText(R.string.settings_sign_out))).perform(click());
            onView(withId(android.R.id.button1)).perform(click());
            waitForDisplayed(withText(R.string.auth_welcome_title));

            // Wrong password is rejected; the right one logs back in.
            onView(withId(R.id.log_in)).perform(click());
            waitForDisplayed(withText(R.string.auth_login_title));
            onView(withId(R.id.email)).perform(replaceText(AuthSteps.TEST_EMAIL));
            onView(withId(R.id.password)).perform(replaceText("wrongpass1"), closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(), click());
            waitForDisplayed(withText(R.string.auth_error_wrong_credentials));
            onView(withId(R.id.password)).perform(replaceText(AuthSteps.TEST_PASSWORD), closeSoftKeyboard());
            onView(withId(R.id.submit)).perform(scrollTo(), click());
            waitForDisplayed(withText(R.string.home_empty_title));
        }
    }
}
