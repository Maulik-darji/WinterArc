package com.app.winterarc.domain.social;

import java.time.Instant;

public record CommunityMember(String communityId, String userId, MemberRole role, Instant joinedAt) {}
