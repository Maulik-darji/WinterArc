package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.waitForDisplayed;
import static org.hamcrest.Matchers.containsString;

import com.app.winterarc.R;

/** Signs up through the debug fake provider (no SMS; code 123456). */
public final class AuthSteps {
    public static final String TEST_NUMBER = "9876543210";
    public static final String TEST_CODE = "123456";

    private AuthSteps() {}

    /** The default region follows the SIM, so tests pick India explicitly via the picker. */
    public static void selectIndia() {
        onView(withId(R.id.country)).perform(click());
        waitForDisplayed(withText(containsString("India (+91)")));
        onView(withText(containsString("India (+91)"))).perform(click());
    }

    public static void signUp() {
        waitForDisplayed(withText(R.string.signup_title));
        selectIndia();
        onView(withId(R.id.phone)).perform(replaceText(TEST_NUMBER));
        onView(withId(R.id.send)).perform(click());
        waitForDisplayed(withText(R.string.otp_title));
        onView(withId(R.id.code)).perform(replaceText(TEST_CODE));
    }
}
