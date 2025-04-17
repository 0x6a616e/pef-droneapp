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
    @GET("missions/get")
    Call<Mission> getMission();

    @GET("missions/process")
    Call<Mission> process();

    @POST("missions/edit")
    Call<Mission> postMission(@Body Mission mission);

    @Multipart
    @POST("missions/uploadfile/drone")
    Call<Void> uploadFile(@Part MultipartBody.Part filePart);
}
