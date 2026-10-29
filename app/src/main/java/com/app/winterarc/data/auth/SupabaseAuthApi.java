package com.app.winterarc.data.auth;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** The slice of Supabase Auth (GoTrue) the app uses. The apikey header is added by the HTTP client. */
public interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    Call<JsonObject> signUp(@Body JsonObject body);

    /** grant_type: password, refresh_token or id_token (Google). */
    @POST("auth/v1/token")
    Call<JsonObject> token(@Query("grant_type") String grantType, @Body JsonObject body);

    @POST("auth/v1/recover")
    Call<JsonObject> recover(@Body JsonObject body);

    @POST("auth/v1/logout")
    Call<Void> logout(@Header("Authorization") String bearer);
}
