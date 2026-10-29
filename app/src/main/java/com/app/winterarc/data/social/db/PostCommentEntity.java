package com.app.winterarc.data.social.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

/**
 * A comment on a post. {@code parentCommentId} makes it a reply (threads). Deleting a comment that
 * has replies keeps a tombstone ({@code deleted}) so the thread stays intact.
 */
@Entity(tableName = "post_comments",
        foreignKeys = @ForeignKey(entity = FeedPostEntity.class, parentColumns = "id", childColumns = "post_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("post_id"), @Index("parent_comment_id")})
public class PostCommentEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull @ColumnInfo(name = "post_id") public String postId = "";
    @Nullable @ColumnInfo(name = "parent_comment_id") public String parentCommentId;
    @NonNull @ColumnInfo(name = "author_user_id") public String authorUserId = "";
    @NonNull public String body = "";
    @NonNull @ColumnInfo(name = "created_at") public Instant createdAt = Instant.EPOCH;
    @Nullable @ColumnInfo(name = "edited_at") public Instant editedAt;
    public boolean deleted;
}
