package com.app.winterarc.domain.feed;

import androidx.annotation.Nullable;

import java.time.Instant;

/** A comment or reply. A deleted comment with replies remains as a tombstone (empty body). */
public record PostComment(String id, String postId, @Nullable String parentId, String authorId, String body,
                          Instant createdAt, @Nullable Instant editedAt, boolean deleted) {
    public boolean isEdited() {
        return editedAt != null && !deleted;
    }
}
