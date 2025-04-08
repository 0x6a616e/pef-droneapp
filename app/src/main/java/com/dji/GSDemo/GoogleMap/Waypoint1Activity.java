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
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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

    private GoogleMap gMap;

    private Button locate, request, edit, start;

    private boolean isEdit = false;
    private boolean isStop = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 10.0f;
    private float mSpeed = 5.0f;

    private float FOV = 78.8f;

    private float photoWaitDistance = calcPhotoWaitDistance(FOV, altitude);

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

        locate.setOnClickListener(this);
        request.setOnClickListener(this);
        edit.setOnClickListener(this);
        start.setOnClickListener(this);

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
            setupGimbal();
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
        String[] filenames = new String[ files.size() ];
        for (int i = 0; i < files.size(); ++i) {
            filenames[i] = files.get(i).getFileName();
        }
        WorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setInputData(
                        new Data.Builder()
                                .putStringArray("FILENAMES", filenames)
                                .build()
                )
                .build();
        WorkManager.getInstance(this).enqueue(uploadWorkRequest);
        ListenableFuture<WorkInfo> future = WorkManager.getInstance(this).getWorkInfoById(uploadWorkRequest.getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Futures.addCallback(
                    future,
                    new FutureCallback<WorkInfo>() {
                        @Override
                        public void onSuccess(WorkInfo result) {
                            deleteFiles(origFiles);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                        }
                    },
                    getApplicationContext().getMainExecutor()
            );
        }
    }

    public void onFileListStateChange(MediaManager.FileListState state) {
        if (state != MediaManager.FileListState.UP_TO_DATE) {
            return;
        }
        List<MediaFile> files = mMediaManager.getSDCardFileListSnapshot();
        assert files != null;
        setResultToToast("Files: " + files.size());
        mFiles.clear();
        File dir = getFilesDir();
        pending = files.size();
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
                    --pending;
                    setResultToToast("Pending: " + pending);
                    mFiles.add(file);
                    if (pending <= 0) {
                        processFiles(mFiles, files);
                    }
                }

                @Override
                public void onFailure(DJIError djiError) {
                    --pending;
                    setResultToToast("Pending: " + pending);
                    if (pending <= 0) {
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
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }

                });
                waypointList.clear();
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.upload:{
                uploadWayPointMission();
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
                waypointMissionBuilder.waypointList(waypointList);
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
                waypointMissionBuilder.waypointList(waypointList);
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

    private void enableDisableEdit(){
        if (isEdit == false) {
            isEdit = true;
            edit.setText("Listo");
        } else {
            isEdit = false;
            optimizeMission();
            configWayPointMission();
            uploadWayPointMission();
            edit.setText("Editar");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
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
                    getWaypointMissionOperator().retryUploadMission(null);
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

        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
    }

}
