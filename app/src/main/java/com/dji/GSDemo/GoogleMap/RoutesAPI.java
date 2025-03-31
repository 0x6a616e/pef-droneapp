package com.dji.GSDemo.GoogleMap;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface RoutesAPI {
    @GET("routes/get")
    Call<Mission> getMission();

    @POST("routes/edit")
    Call<Mission> postMission(@Body Mission mission);
}
