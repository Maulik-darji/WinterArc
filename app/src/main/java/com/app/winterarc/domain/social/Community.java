package com.app.winterarc.domain.social;

import java.time.Instant;

public record Community(String id, String name, String description, String ownerUserId,
                        CommunityPermissions permissions, Instant createdAt) {}
