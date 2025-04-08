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
            double lat = 0.0;
            double lng = 0.0;
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(file);
                GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
                if (gpsDirectory != null) {
                    GeoLocation location = gpsDirectory.getGeoLocation();
                    if (location != null) {
                        lat = location.getLatitude();
                        lng = location.getLongitude();
                    }
                }
            } catch (ImageProcessingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));
            Call<Void> call = routesAPI.uploadFile(lat, lng, filePart);
            call.enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    file.delete();
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                }
            });
        }
        return Result.success();
    }
}
