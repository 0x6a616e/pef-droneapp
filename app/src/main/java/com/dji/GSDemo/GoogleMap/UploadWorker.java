package com.dji.GSDemo.GoogleMap;

import static com.dji.GSDemo.GoogleMap.Waypoint1Activity.BASE_URL;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;

import java.io.File;
import java.io.IOException;

import dji.sdk.media.MediaFile;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        String[] filenames = getInputData().getStringArray("FILENAMES");
        if (filenames == null) {
            return Result.failure();
        }
        File dir = getApplicationContext().getFilesDir();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        for (String filename : filenames) {
            File file = new File(dir, filename);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));
            Call<Void> call = routesAPI.uploadFile(filePart);
            try {
                Response<Void> response = call.execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            file.delete();
            // call.enqueue(new Callback<Void>() {
            //     @Override
            //     public void onResponse(Call<Void> call, Response<Void> response) {
            //         file.delete();
            //     }

            //     @Override
            //     public void onFailure(Call<Void> call, Throwable t) {
            //     }
            // });
        }
        return Result.success();
    }
}
