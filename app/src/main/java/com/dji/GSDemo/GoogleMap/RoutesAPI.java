package com.dji.GSDemo.GoogleMap;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface RoutesAPI {
    @GET("routes/get")
    Call<Mission> getMission();

    @POST("routes/edit")
    Call<Mission> postMission(@Body Mission mission);

    @Multipart
    @POST("routes/uploadfile")
    Call<Void> uploadFile(@Query("lat") Double latitude, @Query("lng") Double longitude, @Part MultipartBody.Part filePart);
}
