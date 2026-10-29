package com.app.winterarc.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** Narrow PostgREST surface used by goal sync. */
public interface SupabaseApi {
    @GET("rest/v1/goals")
    Call<List<CloudGoal>> getGoals(
            @Header("Authorization") String authorization,
            @Query("updated_at") String updatedAtFilter,
            @Query("order") String order);

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/goals")
    Call<List<CloudGoal>> upsertGoal(
            @Header("Authorization") String authorization,
            @Query("on_conflict") String conflictColumn,
            @Body CloudGoal goal);
}
