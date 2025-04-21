package com.dji.GSDemo.GoogleMap;

import com.google.android.gms.maps.model.LatLng;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface RoutesAPI {
    @POST("missions/initialize")
    Call<Void> initialize(@Body LatLng latLng);

    @GET("missions/get")
    Call<Mission> getMission();

    @GET("missions/process")
    Call<Mission> process();

    @POST("missions/edit")
    Call<Mission> postMission(@Body Mission mission);

    @POST("missions/area")
    Call<Mission> postArea(@Body Mission mission);

    @Multipart
    @POST("missions/uploadfile/drone")
    Call<Void> uploadFile(@Part MultipartBody.Part filePart);
}
