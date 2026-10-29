package com.app.winterarc.data.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * PostgREST surface for the shared social data (supabase/migrations 2026-10-24 and 2026-10-27).
 * The website reads and writes the same tables, so both platforms see the same posts.
 * Filters take PostgREST syntax, e.g. {@code "eq." + id}. Writes return the affected rows so an
 * empty array means row-level security filtered the row out (not the author / already gone).
 */
public interface SocialCloudApi {
    String POST_SELECT = "id,author_user_id,body,created_at,edited_at,feed_post_likes(post_id,user_id,created_at)";

    final class RemoteLike {
        @SerializedName("post_id") public String postId;
        @SerializedName("user_id") public String userId;
        @SerializedName("created_at") public String createdAt;
    }

    final class RemotePost {
        @SerializedName("id") public String id;
        @SerializedName("author_user_id") public String authorUserId;
        @SerializedName("body") public String body;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("edited_at") public String editedAt;
        @SerializedName("feed_post_likes") public List<RemoteLike> likes;
    }

    final class RemoteComment {
        @SerializedName("id") public String id;
        @SerializedName("post_id") public String postId;
        @SerializedName("parent_comment_id") public String parentCommentId;
        @SerializedName("author_user_id") public String authorUserId;
        @SerializedName("body") public String body;
        @SerializedName("created_at") public String createdAt;
        @SerializedName("edited_at") public String editedAt;
        @SerializedName("deleted") public boolean deleted;
    }

    final class RemoteProfile {
        @SerializedName("user_id") public String userId;
        @SerializedName("display_name") public String displayName;
        @SerializedName("handle") public String handle;
        @SerializedName("bio") public String bio;
        @SerializedName("avatar_url") public String avatarUrl;
        @SerializedName("banner_url") public String bannerUrl;
    }

    final class RemoteFollow {
        @SerializedName("follower_user_id") public String followerUserId;
        @SerializedName("followed_user_id") public String followedUserId;
        @SerializedName("created_at") public String createdAt;
    }

    // ---- reads (anyone can read; a bearer token is sent when signed in)

    @GET("rest/v1/feed_posts")
    Call<List<RemotePost>> posts(@Header("Authorization") String bearer, @Query("select") String select,
                                 @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/post_comments")
    Call<List<RemoteComment>> comments(@Header("Authorization") String bearer, @Query("select") String select,
                                       @Query("post_id") String postIdIn, @Query("order") String order);

    @GET("rest/v1/profiles")
    Call<List<RemoteProfile>> profiles(@Header("Authorization") String bearer, @Query("select") String select,
                                       @Query("limit") int limit);

    @GET("rest/v1/follows")
    Call<List<RemoteFollow>> follows(@Header("Authorization") String bearer, @Query("select") String select,
                                     @Query("limit") int limit);

    // ---- posts

    @Headers("Prefer: return=representation")
    @POST("rest/v1/feed_posts")
    Call<JsonArray> createPost(@Header("Authorization") String bearer, @Body JsonObject post);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/feed_posts")
    Call<JsonArray> updatePost(@Header("Authorization") String bearer, @Query("id") String idEq, @Body JsonObject changes);

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/feed_posts")
    Call<JsonArray> deletePost(@Header("Authorization") String bearer, @Query("id") String idEq);

    // ---- likes

    @Headers("Prefer: return=representation")
    @POST("rest/v1/feed_post_likes")
    Call<JsonArray> like(@Header("Authorization") String bearer, @Body JsonObject like);

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/feed_post_likes")
    Call<JsonArray> unlike(@Header("Authorization") String bearer, @Query("post_id") String postIdEq,
                           @Query("user_id") String userIdEq);

    // ---- comments

    @Headers("Prefer: return=representation")
    @POST("rest/v1/post_comments")
    Call<JsonArray> createComment(@Header("Authorization") String bearer, @Body JsonObject comment);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/post_comments")
    Call<JsonArray> updateComment(@Header("Authorization") String bearer, @Query("id") String idEq, @Body JsonObject changes);

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/post_comments")
    Call<JsonArray> deleteComment(@Header("Authorization") String bearer, @Query("id") String idEq);

    // ---- profiles and follows

    /** Creates the profile if missing; an existing profile (e.g. made on the website) is kept. */
    @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
    @POST("rest/v1/profiles")
    Call<Void> ensureProfile(@Header("Authorization") String bearer, @Query("on_conflict") String conflictColumn,
                             @Body JsonObject profile);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<JsonArray> updateProfile(@Header("Authorization") String bearer, @Query("user_id") String userIdEq,
                                  @Body JsonObject changes);

    /**
     * Same as {@link #updateProfile} with a pre-serialised JSON body, for changes that must send an
     * explicit {@code null} (removing a photo); the Gson converter drops null members.
     */
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<JsonArray> updateProfileJson(@Header("Authorization") String bearer, @Query("user_id") String userIdEq,
                                      @Body okhttp3.RequestBody json);

    /** Uploads a profile image into the caller's own folder of the public profile-media bucket. */
    @POST("storage/v1/object/profile-media/{path}")
    Call<JsonObject> uploadProfileMedia(@Header("Authorization") String bearer,
                                        @retrofit2.http.Path(value = "path", encoded = true) String path,
                                        @Body okhttp3.RequestBody image);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/follows")
    Call<JsonArray> follow(@Header("Authorization") String bearer, @Body JsonObject follow);

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/follows")
    Call<JsonArray> unfollow(@Header("Authorization") String bearer, @Query("follower_user_id") String followerEq,
                             @Query("followed_user_id") String followedEq);
}
