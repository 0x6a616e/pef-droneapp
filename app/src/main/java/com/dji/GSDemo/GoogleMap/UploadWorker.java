package com.dji.GSDemo.GoogleMap;

import static com.dji.GSDemo.GoogleMap.Waypoint1Activity.BASE_URL;
import static com.dji.GSDemo.GoogleMap.Waypoint1Activity.PROGRESS;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Data;
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
    private int done;
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        setProgressAsync(new Data.Builder().putInt(PROGRESS, 0).build());
    }

    @NonNull
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
        done = 0;
        for (String filename : filenames) {
            File file = new File(dir, filename);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));
            Call<Void> call = routesAPI.uploadFile(filePart);
            call.enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    setProgressAsync(new Data.Builder().putInt(PROGRESS, ++done * 100 / filenames.length).build());
                    file.delete();
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                }
            });
        }
        try {
            wait();
        } catch (InterruptedException e) {
        }
        return Result.success();
    }
}
