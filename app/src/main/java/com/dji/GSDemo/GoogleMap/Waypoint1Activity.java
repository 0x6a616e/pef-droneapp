package com.dji.GSDemo.GoogleMap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.xml.transform.Result;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.L;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Waypoint1Activity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, OnMapReadyCallback, MediaManager.FileListStateListener {

    protected static final String TAG = "GSDemoActivity";

    protected static final String BASE_URL = "https://api.pef.estupideznatural.tech/";
    protected static final String PROGRESS = "PROGRESS";

    private GoogleMap gMap;

    private Button locate, request, edit, start, edit_umbral;

    private boolean isEdit = false;
    private boolean isStop = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 10.0f;
    private float mSpeed = 5.0f;

    private float FOV = 78.8f;

    private float photoWaitDistance = calcPhotoWaitDistance(FOV, altitude);

    private boolean firstTravel = true;

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private Camera mCamera;
    private Gimbal mGimbal;
    private MediaManager mMediaManager;
    private List<MediaFile> mFiles = new ArrayList<>();
    private int pending;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;

    Inequality[] modal_status = {Inequality.LESS_THAN, Inequality.LESS_THAN, Inequality.LESS_THAN, Inequality.LESS_THAN};
    HashMap<String, Integer> result_filter = new HashMap<>(4);

    private int done;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightComponents();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        Waypoint1Activity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Waypoint1Activity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private float calcPhotoWaitDistance(float FOV, float altitude) {
        return (float) (Math.tan(Math.toRadians(FOV / 2)) * altitude * 2);
    }

    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        request = (Button) findViewById(R.id.request);
        edit = (Button) findViewById(R.id.edit);
        start = (Button) findViewById(R.id.start);
        edit_umbral = (Button) findViewById(R.id.params);

        locate.setOnClickListener(this);
        request.setOnClickListener(this);
        edit.setOnClickListener(this);
        start.setOnClickListener(this);
        edit_umbral.setOnClickListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_waypoint1);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        addListener();

        initFlightComponents();
        setupGimbal();
        setupCamera();
        initializeMission();

        result_filter.put("0", 0);
        result_filter.put("1", 0);
        result_filter.put("2", 0);
        result_filter.put("3", 0);
        result_filter.put("4", 0);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightComponents();
        setupGimbal();
        setupCamera();
        initializeMission();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightComponents() {

        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
            mGimbal = product.getGimbal();
            mCamera = product.getCamera();
            mMediaManager = mCamera.getMediaManager();
            mMediaManager.addUpdateStorageLocationListener(SettingsDefinitions.StorageLocation.SDCARD, this);
            if (waypointMissionBuilder == null) {
                waypointMissionBuilder = new WaypointMission.Builder();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                }
            });
        }
    }

    private void setupGimbal() {
        Rotation rotationPitch = new Rotation.Builder()
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .pitch(-90f)
                .time(1)
                .build();
        mGimbal.rotate(rotationPitch, null);
    }

    private void setupCamera() {
        mCamera.setFocusMode(SettingsDefinitions.FocusMode.AFC, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null) {
                    setResultToToast("Error Camera: " + djiError.getDescription());
                }
            }
        });
    }

    private void initializeMission() {
        setResultToToast("Iniciando misión.");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        LatLng latLng = new LatLng(droneLocationLat, droneLocationLng);
        Call<Void> call = routesAPI.initialize(latLng);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
            }
        });
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
        }

        @Override
        public void onExecutionStart() {
            mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, null);
        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            start.setText("Iniciar");
            isStop = false;
            mCamera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, null);
            uploadPictures();
        }
    };

    private void deleteFiles(List<MediaFile> files) {
        setResultToToast("Cleaning files");
        mMediaManager.deleteFiles(files, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
            @Override
            public void onSuccess(List<MediaFile> mediaFiles, DJICameraError djiCameraError) {
                if (djiCameraError != null) {
                    setResultToToast("Delete 1 error: " + djiCameraError.getDescription());
                }
            }

            @Override
            public void onFailure(DJIError djiError) {
                if (djiError != null) {
                    setResultToToast("Delete 2 error: " + djiError.getDescription());
                }

            }
        });
        mFiles.clear();
    }

    private void processFiles(List<MediaFile> files, List<MediaFile> origFiles) {
        LinearLayout progressBarDialog = (LinearLayout) getLayoutInflater().inflate(R.layout.progress_bar, null);
        ProgressBar progressBar = progressBarDialog.findViewById(R.id.progressBar2);
        TextView progressBarTitle = progressBarDialog.findViewById(R.id.textView3);
        progressBarTitle.setText("Subiendo imágenes al servidor");
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("")
                .setView(progressBarDialog)
                .create();
        alertDialog.show();
        File dir = getFilesDir();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        done = 0;
        for (MediaFile mediaFile : files) {
            File file = new File(dir, mediaFile.getFileName());
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));
            Call<Void> call = routesAPI.uploadFile(filePart);
            call.enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    int percentage = ++done * 100 / files.size();
                    progressBar.setProgress(percentage);
                    // setResultToToast("Porcentaje de subida: " + percentage + "%");
                    file.delete();
                    if (percentage >= 100) {
                        alertDialog.cancel();
                        deleteFiles(origFiles);
                        processMission();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                }
            });
        }
    }

    public void onFileListStateChange(MediaManager.FileListState state) {
        if (state != MediaManager.FileListState.UP_TO_DATE) {
            return;
        }
        List<MediaFile> files = mMediaManager.getSDCardFileListSnapshot();
        if (files == null || files.size() == 0) {
            return;
        }
        setResultToToast("Files: " + files.size());
        mFiles.clear();
        File dir = getFilesDir();
        done = 0;
        LinearLayout progressBarDialog = (LinearLayout) getLayoutInflater().inflate(R.layout.progress_bar, null);
        ProgressBar progressBar = progressBarDialog.findViewById(R.id.progressBar2);
        TextView progressBarTitle = progressBarDialog.findViewById(R.id.textView3);
        progressBarTitle.setText("Descargando imágenes del dron");
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("")
                .setView(progressBarDialog)
                .create();
        alertDialog.show();
        for (MediaFile file : files) {
            file.fetchFileData(dir, null, new DownloadListener<String>() {
                @Override
                public void onStart() {
                }

                @Override
                public void onRateUpdate(long l, long l1, long l2) {
                }

                @Override
                public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {
                }

                @Override
                public void onProgress(long l, long l1) {
                }

                @Override
                public void onSuccess(String s) {
                    int percentage = ++done * 100 / files.size();
                    progressBar.setProgress(percentage);
                    mFiles.add(file);
                    if (percentage >= 100) {
                        alertDialog.cancel();
                        processFiles(mFiles, files);
                    }
                }

                @Override
                public void onFailure(DJIError djiError) {
                    int percentage = ++done * 100 / files.size();
                    progressBar.setProgress(percentage);
                    if (percentage >= 100) {
                        alertDialog.cancel();
                        processFiles(mFiles, files);
                    }
                }
            });
        }
    }

    private void uploadPictures() {
        setResultToToast("Subiendo imagenes");
        mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null) {
                    setResultToToast("Refresh error: " + djiError.getDescription());
                }
            }
        });
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null){
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object
        gMap.setOnMarkerClickListener(this);

    }

    @Override
    public void onMapClick(LatLng point) {
        if (isEdit){
            addPoint(point);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (marker != droneMarker) {
            marker.remove();
            List<Waypoint> toKeep = new ArrayList<>();
            for (Waypoint waypoint : waypointList) {
                if (waypoint.coordinate.getLatitude() != marker.getPosition().latitude ||
                        waypoint.coordinate.getLongitude() != marker.getPosition().longitude) {
                    toKeep.add(waypoint);
                }
            }
            waypointList.retainAll(toKeep);
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        }
        return true;
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    private void enableDisableStop() {
        if (isStop) {
            start.setText("Iniciar");
            stopWaypointMission();
        } else {
            start.setText("Detener");
            startWaypointMission();
        }
        isStop = !isStop;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.request: {
                requestMission();
                break;
            }
            case R.id.edit:{
                enableDisableEdit();
                break;
            }
            case R.id.start:{
                enableDisableStop();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            case R.id.params: {
                showSettingDialog();
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }

    private void addPoint(LatLng point) {
        markWaypoint(point);
        Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
        if (waypointMissionBuilder == null) {
            waypointMissionBuilder = new WaypointMission.Builder();
        }
        waypointList.add(mWaypoint);
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
    }

    private void processMission() {
        setResultToToast("Solicitando misión al servidor.");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        Call<Mission> call = routesAPI.process(result_filter);
        call.enqueue(new Callback<Mission>() {
            @Override
            public void onResponse(Call<Mission> call, Response<Mission> response) {
                Mission mission = response.body();
                assert mission != null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                waypointList.clear();
                updateDroneLocation();
                if (mission.getWaypoints().size() == 0) {
                    setResultToToast("No se detectaron puntos de riesgo");
                } else {
                    for (LatLng p : mission.getWaypoints()) {
                        addPoint(p);
                    }
                    configWayPointMission();
                    uploadWayPointMission();
                }
            }

            @Override
            public void onFailure(Call<Mission> call, Throwable t) {
                setResultToToast("Fallo en la solicitud al servidor.");
            }
        });
    }

    private void requestMission() {
        setResultToToast("Solicitando misión al servidor.");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        Call<Mission> call = routesAPI.getMission();
        call.enqueue(new Callback<Mission>() {
            @Override
            public void onResponse(Call<Mission> call, Response<Mission> response) {
                Mission mission = response.body();
                assert mission != null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                waypointList.clear();
                updateDroneLocation();
                for (LatLng p : mission.getWaypoints()) {
                    addPoint(p);
                }
                configWayPointMission();
                uploadWayPointMission();
            }

            @Override
            public void onFailure(Call<Mission> call, Throwable t) {
                setResultToToast("Fallo en la solicitud al servidor.");
            }
        });
    }

    private void optimizeMission() {
        LatLng start = new LatLng(droneLocationLat, droneLocationLng);
        List<LatLng> waypoints = new ArrayList<>();
        for (Waypoint w : waypointList) {
            waypoints.add(new LatLng(w.coordinate.getLatitude(), w.coordinate.getLongitude()));
        }
        Mission mission = new Mission(start, waypoints);
        setResultToToast("Optimizando ruta");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        Call<Mission> call = routesAPI.postMission(mission);
        call.enqueue(new Callback<Mission>() {
            @Override
            public void onResponse(Call<Mission> call, Response<Mission> response) {
                Mission optimizedMission = response.body();
                assert optimizedMission != null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                waypointList.clear();
                updateDroneLocation();
                for (LatLng p : optimizedMission.getWaypoints()) {
                    addPoint(p);
                }
                configWayPointMission();
                uploadWayPointMission();
            }

            @Override
            public void onFailure(Call<Mission> call, Throwable t) {
                setResultToToast("Fallo en la solicitud al servidor.");
            }
        });
    }

    private void optimizeArea() {
        LatLng start = new LatLng(droneLocationLat, droneLocationLng);
        List<LatLng> waypoints = new ArrayList<>();
        for (Waypoint w : waypointList) {
            waypoints.add(new LatLng(w.coordinate.getLatitude(), w.coordinate.getLongitude()));
        }
        Mission mission = new Mission(start, waypoints);
        setResultToToast("Optimizando área");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        RoutesAPI routesAPI = retrofit.create(RoutesAPI.class);
        Call<Mission> call = routesAPI.postArea(mission);
        call.enqueue(new Callback<Mission>() {
            @Override
            public void onResponse(Call<Mission> call, Response<Mission> response) {
                Mission optimizedMission = response.body();
                assert optimizedMission != null;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                waypointList.clear();
                updateDroneLocation();
                for (LatLng p : optimizedMission.getWaypoints()) {
                    addPoint(p);
                }
                configWayPointMission();
                uploadWayPointMission();
            }

            @Override
            public void onFailure(Call<Mission> call, Throwable t) {
                setResultToToast("Fallo en la solicitud al servidor.");
            }
        });
        firstTravel = false;
    }

    private void enableDisableEdit(){
        if (isEdit == false) {
            isEdit = true;
            edit.setText("Listo");
        } else {
            isEdit = false;
            if (firstTravel) {
                optimizeArea();
            } else {
                optimizeMission();
            }
            edit.setText("Editar");
        }
    }

    enum Inequality {
        LESS_THAN,
        GREATER_THAN
    }

    private Inequality switch_inequality(Inequality i) {
        if (i == Inequality.LESS_THAN) {
            return Inequality.GREATER_THAN;
        } else if (i == Inequality.GREATER_THAN) {
            return Inequality.LESS_THAN;
        }
        return i;
    }

    private String print_inequality(Inequality i) {
        if (i == Inequality.LESS_THAN) {
            return "Menor que";
        } else if (i == Inequality.GREATER_THAN) {
            return "Mayor que";
        }
        return "";
    }

    private int get_sign(Inequality i) {
        if (i == Inequality.LESS_THAN) {
            return -1;
        } else if (i == Inequality.GREATER_THAN) {
            return 1;
        }
        return 0;
    }

    private void showSettingDialog(){
        ScrollView wayPointSettings = (ScrollView) getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        Button[] buttons = {
                (Button) wayPointSettings.findViewById(R.id.button_class1),
                (Button) wayPointSettings.findViewById(R.id.button_class2),
                (Button) wayPointSettings.findViewById(R.id.button_class3),
                (Button) wayPointSettings.findViewById(R.id.button_class4),
        };

        EditText[] inputs = {
                (EditText) wayPointSettings.findViewById(R.id.editTextNumber_class1),
                (EditText) wayPointSettings.findViewById(R.id.editTextNumber_class2),
                (EditText) wayPointSettings.findViewById(R.id.editTextNumber_class3),
                (EditText) wayPointSettings.findViewById(R.id.editTextNumber_class4),
        };

        for (int i = 0; i < 4; ++i) {
            final Button btn = buttons[i];
            int finalI = i;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    modal_status[finalI] = switch_inequality(modal_status[finalI]);
                    btn.setText(print_inequality(modal_status[finalI]));
                }
            });
        }

        RadioGroup altitudeRG = (RadioGroup) wayPointSettings.findViewById(R.id.altitude);
        altitudeRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.lowAltitude) {
                    altitude = 10f;
                } else if (i == R.id.midAltitude) {
                    altitude = 15f;
                } else if (i == R.id.highAltitude) {
                    altitude = 20f;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Listo",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        for (int i = 0; i < 4; ++i) {
                            int value = Integer.parseInt("0" + inputs[i].getText().toString());
                            int sign = get_sign(modal_status[i]);
                            value *= sign;
                            result_filter.put(String.valueOf(i + 1), value);
                        }
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void configWayPointMission(){

        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){

            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
                waypointMissionBuilder.getWaypointList().get(i).heading = 0;
                waypointMissionBuilder.getWaypointList().get(i).shootPhotoDistanceInterval = photoWaitDistance;
            }
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    private void uploadWayPointMission(){

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gMap.clear();
                        }
                    });
                    waypointList.clear();
                    updateDroneLocation();
                    firstTravel = true;
                }
            }
        });

    }

    private void startWaypointMission(){

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }

    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
    }

}
