package com.app.winterarc.domain.feed;

import androidx.annotation.Nullable;

import java.time.Instant;

public record FeedPost(String id, String authorId, String body, Instant createdAt, @Nullable Instant editedAt,
                       int likeCount, boolean likedByMe, int replyCount) {
    public boolean isEdited() {
        return editedAt != null;
    }
}
