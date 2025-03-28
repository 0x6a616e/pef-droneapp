package com.dji.GSDemo.GoogleMap;

import retrofit2.Call;
import retrofit2.http.GET;

public interface RoutesAPI {
    @GET("routes/get")
    Call<Mission> getMission();
}
