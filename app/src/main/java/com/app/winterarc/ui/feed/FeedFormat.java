package com.app.winterarc.ui.feed;

import android.content.Context;

import com.app.winterarc.R;
import com.app.winterarc.domain.social.SocialUser;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

/** Display formatting for posts and comments: author labels, relative times and counts. */
final class FeedFormat {
    private FeedFormat() {}

    static String name(Context context, Map<String, SocialUser> users, String userId) {
        SocialUser user = users.get(userId);
        return user == null || user.displayName().isBlank() ? context.getString(R.string.feed_member_name) : user.displayName();
    }

    static String handle(Context context, Map<String, SocialUser> users, String userId) {
        SocialUser user = users.get(userId);
        return user == null || user.handle().isBlank() ? context.getString(R.string.feed_member_handle) : user.handle();
    }

    static String initial(Context context, Map<String, SocialUser> users, String userId) {
        String name = name(context, users, userId);
        return name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.getDefault());
    }

    @androidx.annotation.Nullable
    static String avatarUrl(Map<String, SocialUser> users, String userId) {
        SocialUser user = users.get(userId);
        return user == null ? null : user.avatarUrl();
    }

    /** Shows the person's photo, or their initial when they have none. */
    static void avatar(android.widget.TextView view, Map<String, SocialUser> users, String userId) {
        com.app.winterarc.ui.common.Avatars.bind(view, initial(view.getContext(), users, userId), avatarUrl(users, userId));
    }

    /** "now", "5m", "3h", "4d", then "Oct 4" (this year) or "Oct 4, 2025". */
    static String relative(Context context, Instant time, Instant now, ZoneId zone) {
        Duration age = Duration.between(time, now);
        if (age.toMinutes() < 1) return context.getString(R.string.feed_now);
        if (age.toHours() < 1) return context.getString(R.string.feed_minutes, (int) age.toMinutes());
        if (age.toDays() < 1) return context.getString(R.string.feed_hours, (int) age.toHours());
        if (age.toDays() < 7) return context.getString(R.string.feed_days, (int) age.toDays());
        ZonedDateTime at = time.atZone(zone);
        String pattern = at.getYear() == now.atZone(zone).getYear() ? "MMM d" : "MMM d, yyyy";
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(at);
    }

    /** "9:41 AM · Oct 24, 2026" in the user's locale. */
    static String exact(Instant time, ZoneId zone) {
        ZonedDateTime at = time.atZone(zone);
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(at)
                + " · " + DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(at);
    }

    /** "@handle · 3h · Edited" for a row header. */
    static String meta(Context context, String handle, String time, boolean edited) {
        String dot = " " + context.getString(R.string.feed_dot_separator) + " ";
        return handle + dot + time + (edited ? dot + context.getString(R.string.feed_edited) : "");
    }

    /** Empty for zero (X-style), otherwise a compact count: 7, 1.2K, 3.4M. */
    static String count(int value) {
        if (value <= 0) return "";
        if (value < 1000) return String.valueOf(value);
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setMaximumFractionDigits(1);
        return value < 1_000_000 ? format.format(value / 1000.0) + "K" : format.format(value / 1_000_000.0) + "M";
    }
}
