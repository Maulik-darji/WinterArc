package com.app.winterarc.domain.feed;

import androidx.annotation.Nullable;

/** Input rules shared by the UI (live counters) and the repository (enforcement). */
public final class FeedRules {
    public static final int POST_MAX = 280;
    public static final int COMMENT_MAX = 500;
    /** Replies deeper than this are shown at this indent with "Replying to @handle". */
    public static final int MAX_VISUAL_DEPTH = 3;

    private FeedRules() {}

    /** Trims, collapses runs of 3+ blank lines; returns null when the text is empty or too long. */
    @Nullable
    public static String clean(@Nullable String text, int max) {
        if (text == null) return null;
        String t = text.trim().replaceAll("\\n{3,}", "\n\n");
        if (t.isEmpty() || t.codePointCount(0, t.length()) > max) return null;
        return t;
    }

    /** Characters left for a live counter (negative when over the limit). */
    public static int remaining(@Nullable String text, int max) {
        String t = text == null ? "" : text.trim();
        return max - t.codePointCount(0, t.length());
    }
}
