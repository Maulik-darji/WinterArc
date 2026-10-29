package com.app.winterarc.domain.auth;

import androidx.annotation.Nullable;

/**
 * A signed-in account. {@code uid} is the provider's stable id (also the Supabase JWT subject),
 * used for social authorship and cloud sync.
 */
public record AuthUser(String uid, @Nullable String email, @Nullable String displayName, boolean emailVerified) {

    /** A friendly name: the display name, else the part of the email before "@". */
    public String bestName() {
        if (displayName != null && !displayName.isBlank()) return displayName.trim();
        if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));
        return "WinterArc member";
    }
}
