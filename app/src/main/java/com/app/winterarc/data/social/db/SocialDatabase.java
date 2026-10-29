package com.app.winterarc.data.social.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.app.winterarc.data.db.Converters;

@Database(entities = {
        SocialUserEntity.class,
        CommunityEntity.class,
        CommunityMemberEntity.class,
        CommunityPostEntity.class,
        PostLikeEntity.class,
        FollowEntity.class,
        CommunityMessageEntity.class,
        FeedPostEntity.class,
        FeedPostLikeEntity.class,
        PostCommentEntity.class
}, version = SocialDatabase.VERSION, exportSchema = true)
@TypeConverters(Converters.class)
public abstract class SocialDatabase extends RoomDatabase {
    public static final String NAME = "winterarc_social.db";
    public static final int VERSION = 3;

    /** v2 adds the Feed: posts, post likes and threaded comments. Existing data is untouched. */
    /** v3: profile photo and banner (public image URLs, shared with the website). */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `social_users` ADD COLUMN `avatar_url` TEXT");
            db.execSQL("ALTER TABLE `social_users` ADD COLUMN `banner_url` TEXT");
        }
    };

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `feed_posts` (`id` TEXT NOT NULL, `author_user_id` TEXT NOT NULL, "
                    + "`body` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `edited_at` INTEGER, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_posts_author_user_id` ON `feed_posts` (`author_user_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_posts_created_at` ON `feed_posts` (`created_at`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `feed_post_likes` (`post_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, "
                    + "`created_at` INTEGER NOT NULL, PRIMARY KEY(`post_id`, `user_id`), FOREIGN KEY(`post_id`) "
                    + "REFERENCES `feed_posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_post_likes_post_id` ON `feed_post_likes` (`post_id`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `post_comments` (`id` TEXT NOT NULL, `post_id` TEXT NOT NULL, "
                    + "`parent_comment_id` TEXT, `author_user_id` TEXT NOT NULL, `body` TEXT NOT NULL, "
                    + "`created_at` INTEGER NOT NULL, `edited_at` INTEGER, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), "
                    + "FOREIGN KEY(`post_id`) REFERENCES `feed_posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_post_comments_post_id` ON `post_comments` (`post_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_post_comments_parent_comment_id` ON `post_comments` (`parent_comment_id`)");
        }
    };

    public abstract SocialDao socialDao();

    public abstract FeedDao feedDao();
}
