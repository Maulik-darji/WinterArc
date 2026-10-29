package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.waitForDisplayed;

import com.app.winterarc.R;

/** Creates an account through the debug local test provider (no network, no real email). */
public final class AuthSteps {
    public static final String TEST_NAME = "Test Member";
    public static final String TEST_EMAIL = "member@example.test";
    public static final String TEST_PASSWORD = "winter2026";

    private AuthSteps() {}

    public static void openSignUp() {
        waitForDisplayed(withText(R.string.auth_welcome_title));
        onView(withId(R.id.create_account)).perform(click());
        waitForDisplayed(withText(R.string.auth_signup_title));
    }

    public static void fillSignUp(String name, String email, String password, String confirm) {
        onView(withId(R.id.name)).perform(scrollTo(), replaceText(name));
        onView(withId(R.id.email)).perform(scrollTo(), replaceText(email));
        onView(withId(R.id.password)).perform(scrollTo(), replaceText(password));
        onView(withId(R.id.confirm)).perform(scrollTo(), replaceText(confirm), closeSoftKeyboard());
        onView(withId(R.id.submit)).perform(scrollTo(), click());
    }

    public static void signUp() {
        openSignUp();
        fillSignUp(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
    }
}
