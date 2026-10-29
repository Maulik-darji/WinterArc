package com.app.winterarc.domain.social;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Builds a readable @handle for a new profile from the account's email or name. */
public final class SocialHandles {
    public static final int MAX = 15;

    private SocialHandles() {}

    public static String fromAccount(@Nullable String email, @Nullable String name) {
        String base = null;
        if (email != null && email.contains("@")) base = email.substring(0, email.indexOf('@'));
        if (base == null || base.isBlank()) base = name;
        String cleaned = base == null ? "" : base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleaned.isEmpty()) cleaned = "member";
        if (cleaned.length() > MAX) cleaned = cleaned.substring(0, MAX);
        return "@" + cleaned;
    }
}
