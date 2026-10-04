package com.app.winterarc.domain.social;

import java.time.Instant;

public record Follow(String followerUserId, String followedUserId, Instant createdAt) {}
