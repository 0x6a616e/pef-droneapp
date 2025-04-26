package com.dji.GSDemo.GoogleMap;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
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

    @POST("missions/process")
    Call<Mission> process(@Body Map<String, Integer> data);

    @POST("missions/edit")
    Call<Mission> postMission(@Body Mission mission);

    @POST("missions/area")
    Call<Mission> postArea(@Body Mission mission);

    @Multipart
    @POST("missions/uploadfile/drone")
    Call<Void> uploadFile(@Part MultipartBody.Part filePart);
}
