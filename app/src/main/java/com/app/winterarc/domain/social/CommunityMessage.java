package com.app.winterarc.domain.social;

import java.time.Instant;

public record CommunityMessage(String id, String communityId, String senderUserId,
                               String body, Instant createdAt) {}
