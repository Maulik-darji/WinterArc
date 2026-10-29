package com.app.winterarc.ui;

import static androidx.test.espresso.Espresso.onView;
import androidx.test.espresso.Espresso;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.app.winterarc.ui.Waits.waitForDisplayed;
import static com.app.winterarc.ui.Waits.waitForGone;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Post → open → comment → threaded reply → edit → delete, end to end. */
@RunWith(AndroidJUnit4.class)
public class FeedFlowTest {
    private static final String POST = "Day 12 of my winter run streak.";
    private static final String COMMENT = "Keep it going!";
    private static final String REPLY = "Thanks, tomorrow is a long one.";
    private static final String EDITED = "Thanks, tomorrow is 10 km.";

    @Test
    public void postCommentReplyEditDelete() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitForDisplayed(withId(R.id.skip));
            onView(withId(R.id.skip)).perform(click());
            AuthSteps.signUp();
            waitForDisplayed(withText(R.string.home_empty_title));

            // Feed opens on Posts with an empty state; write a post.
            onView(withId(R.id.navigation_feed)).perform(click());
            waitForDisplayed(withText(R.string.feed_empty_title));
            onView(withId(R.id.compose)).perform(click());
            onView(withId(R.id.body)).perform(replaceText(POST), closeSoftKeyboard());
            onView(withId(R.id.post)).perform(click());
            waitForDisplayed(withText(POST));

            // Open it and comment.
            onView(withText(POST)).perform(click());
            waitForDisplayed(withText(R.string.comments_empty));
            onView(withId(R.id.reply_input)).perform(replaceText(COMMENT));
            onView(withId(R.id.send)).perform(click());
            waitForDisplayed(withText(COMMENT));

            // Tapping the commenter's avatar opens their profile.
            onView(allOf(withId(R.id.avatar), isDescendantOfA(allOf(withId(R.id.comment_root),
                    hasDescendant(withText(COMMENT)))))).perform(click());
            waitForDisplayed(withId(R.id.followers));
            Espresso.pressBack();
            waitForDisplayed(withText(COMMENT));

            // Reply to the comment: the composer shows who you're replying to.
            onView(allOf(withId(R.id.reply), isDescendantOfA(allOf(withId(R.id.comment_root),
                    hasDescendant(withText(COMMENT)))))).perform(click());
            waitForDisplayed(withText(containsString("Replying to @member")));
            onView(withId(R.id.reply_input)).perform(replaceText(REPLY));
            onView(withId(R.id.send)).perform(click());
            waitForDisplayed(withText(REPLY));

            // Edit the reply.
            onView(allOf(withId(R.id.more), isDescendantOfA(allOf(withId(R.id.comment_root),
                    hasDescendant(withText(REPLY)))))).perform(click());
            onView(withText(R.string.comment_edit)).perform(click());
            onView(withId(R.id.input)).perform(replaceText(EDITED));
            onView(withId(android.R.id.button1)).perform(click());
            waitForDisplayed(withText(EDITED));

            // Delete the parent comment: it has a reply, so it becomes a placeholder.
            onView(allOf(withId(R.id.more), isDescendantOfA(allOf(withId(R.id.comment_root),
                    hasDescendant(withText(COMMENT)))))).perform(click());
            onView(withText(R.string.comment_delete)).perform(click());
            onView(withId(android.R.id.button1)).perform(click());
            waitForDisplayed(withText(R.string.comment_deleted));
            waitForGone(withText(COMMENT));
            waitForDisplayed(withText(EDITED));
        }
    }
}
