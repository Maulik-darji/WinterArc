package com.app.winterarc.domain.social;

import java.time.Instant;

public record CommunityPost(String id, String communityId, String authorUserId, String body,
                            Instant createdAt, int likeCount, boolean likedByMe) {}
