package com.app.winterarc.data.social.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.app.winterarc.data.db.Converters;

@Database(entities = {
        SocialUserEntity.class,
        CommunityEntity.class,
        CommunityMemberEntity.class,
        CommunityPostEntity.class,
        PostLikeEntity.class,
        FollowEntity.class,
        CommunityMessageEntity.class
}, version = 1, exportSchema = true)
@TypeConverters(Converters.class)
public abstract class SocialDatabase extends RoomDatabase {
    public static final String NAME = "winterarc_social.db";
    public abstract SocialDao socialDao();
}
