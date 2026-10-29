package com.app.winterarc.domain.social;

import androidx.annotation.Nullable;

/** A member's public profile. Image URLs are null until a photo or banner is set. */
public record SocialUser(String id, String displayName, String handle, String bio, String initial,
                         @Nullable String avatarUrl, @Nullable String bannerUrl) {}
