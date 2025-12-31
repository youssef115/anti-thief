package com.youssef.anti_thief.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.Gson;
import com.youssef.anti_thief.DTO.EncryptedPayload;
import com.youssef.anti_thief.DTO.LocationPayload;
import com.youssef.anti_thief.MainActivity;
import com.youssef.anti_thief.config.Config;
import com.youssef.anti_thief.utils.AESEncryption;
import com.youssef.anti_thief.utils.HiddenCameraActivity;
import com.youssef.anti_thief.utils.LocationCache;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TrackingService extends Service {

    private static final String TAG = "TrackingService";
    private static final String CHANNEL_ID = "tracking_channel";
    private static final String CAMERA_CHANNEL_ID = "camera_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int CAMERA_NOTIFICATION_ID = 2;
    private static final long LOCATION_INTERVAL = 60000;
    private static final long SYNC_INTERVAL = 60000;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ApiService apiService;
    private LocationCache locationCache;
    private Handler syncHandler;
    private Runnable syncRunnable;
    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(TAG, "Screen turned ON - launching camera");
                launchHiddenCamera();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        Config.init(this);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "antithief:tracking");
        wakeLock.acquire();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCache = new LocationCache(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        syncHandler = new Handler(Looper.getMainLooper());
        syncRunnable = this::syncCachedLocations;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        Config.init(this);

        String serverUrl = Config.getServerUrl();
        String apiKey = Config.getApiKey();
        Log.d(TAG, "Using server URL: " + serverUrl);
        Log.d(TAG, "API Key configured: " + (apiKey != null && !apiKey.isEmpty() ? "YES" : "NO"));

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

        if (apiKey != null && !apiKey.isEmpty()) {
            httpClientBuilder.addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder()
                        .header("X-API-Key", apiKey)
                        .header("Content-Type", "application/json");
                return chain.proceed(requestBuilder.build());
            });
            Log.d(TAG, "API key interceptor added to HTTP client");
        }

        OkHttpClient httpClient = httpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        createNotificationChannels();
        startForeground(NOTIFICATION_ID, createNotification());
        startLocationUpdates();

        syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);

        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the tracking service running");
            channel.setShowBadge(false);

            NotificationChannel cameraChannel = new NotificationChannel(
                    CAMERA_CHANNEL_ID,
                    "Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            cameraChannel.setDescription("Security camera alerts");
            cameraChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            manager.createNotificationChannel(cameraChannel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL / 2)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    handleNewLocation(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "Location updates started");
    }

    private void handleNewLocation(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String deviceId = getUniqueDeviceId();

        boolean added = locationCache.addLocation(lat, lng, deviceId);
        if (added) {
            Log.d(TAG, "New location cached: " + lat + ", " + lng);
        } else {
            Log.d(TAG, "Location skipped (too soon): " + lat + ", " + lng);
        }
    }

    private void syncCachedLocations() {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network, skipping sync");
            syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
            return;
        }

        List<LocationCache.CachedLocation> cachedLocations = locationCache.getUnsyncedLocations();

        if (cachedLocations.isEmpty()) {
            Log.d(TAG, "No locations to sync");
            syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
            return;
        }

        List<LocationPayload> payloads = new ArrayList<>();
        for (LocationCache.CachedLocation cached : cachedLocations) {
            payloads.add(new LocationPayload(cached.latitude, cached.longitude, cached.deviceId, cached.timestamp));
        }

        Log.d(TAG, "Syncing " + payloads.size() + " locations");

        String aesKey = Config.getAesKey();
        Log.d(TAG, "AES Key configured: " + (aesKey != null && !aesKey.isEmpty() ? "YES (length=" + aesKey.length() + ")" : "NO"));

        if (aesKey != null && !aesKey.isEmpty()) {
            Log.d(TAG, ">>> Using ENCRYPTED endpoint: /api/secure/location");
            sendEncryptedLocations(payloads);
        } else {
            Log.d(TAG, ">>> Using UNENCRYPTED endpoint: /api/location");
            sendUnencryptedLocations(payloads);
        }
    }

    private void sendEncryptedLocations(List<LocationPayload> payloads) {
        try {
            Gson gson = new Gson();
            String jsonPayload = gson.toJson(payloads);

            String encryptedData = AESEncryption.encrypt(jsonPayload);
            if (encryptedData == null) {
                Log.e(TAG, "Encryption failed, falling back to unencrypted");
                sendUnencryptedLocations(payloads);
                return;
            }

            EncryptedPayload encryptedPayload = new EncryptedPayload(encryptedData, getUniqueDeviceId());

            Log.d(TAG, "Sending encrypted batch: " + payloads.size() + " locations");

            apiService.sendEncryptedLocation(encryptedPayload).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.code() == 200) {
                        Log.d(TAG, "Encrypted sync successful (200): " + payloads.size() + " locations sent");
                        locationCache.clearSyncedLocations();
                    } else {
                        Log.e(TAG, "Encrypted sync failed: " + response.code() + " - keeping cache for retry");
                    }
                    locationCache.clearOldLocations(72);
                    syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Encrypted sync failed - keeping cache for retry", t);
                    locationCache.clearOldLocations(72);
                    syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error encrypting locations", e);
            sendUnencryptedLocations(payloads);
        }
    }

    private void sendUnencryptedLocations(List<LocationPayload> payloads) {
        Log.d(TAG, "Sending unencrypted batch: " + payloads.size() + " locations");

        apiService.sendLocationBatch(payloads).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.code() == 200) {
                    Log.d(TAG, "Batch sync successful (200): " + payloads.size() + " locations sent");
                    locationCache.clearSyncedLocations();
                } else {
                    Log.e(TAG, "Batch sync failed: " + response.code() + " - keeping cache for retry");
                }
                locationCache.clearOldLocations(72);
                syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Batch sync failed - keeping cache for retry", t);
                locationCache.clearOldLocations(72);
                syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL);
            }
        });
    }

    private void launchHiddenCamera() {
        Log.d(TAG, "Launching HiddenCameraActivity for camera capture");

        Intent cameraIntent = new Intent(this, HiddenCameraActivity.class);
        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NO_ANIMATION);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, cameraIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CAMERA_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Security Check")
                .setContentText("Verifying...")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(CAMERA_NOTIFICATION_ID, builder.build());

        try {
            Log.d(TAG, "Attempting direct activity launch");
            startActivity(cameraIntent);
        } catch (Exception e) {
            Log.e(TAG, "Direct activity launch failed: " + e.getMessage());
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            notificationManager.cancel(CAMERA_NOTIFICATION_ID);
        }, 10000);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private String getUniqueDeviceId() {
        return Build.MANUFACTURER + "_" + Build.MODEL + "_" + Build.SERIAL;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed - scheduling restart");

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (syncHandler != null) {
            syncHandler.removeCallbacks(syncRunnable);
        }

        scheduleServiceRestart();
    }

    private void scheduleServiceRestart() {
        Log.d(TAG, "Scheduling service restart...");

        Intent restartIntent = new Intent(this, TrackingService.class);
        restartIntent.setAction("RESTART_SERVICE");

        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);

        long restartTime = System.currentTimeMillis() + 1000;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    restartTime,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    restartTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "Service restart scheduled for 1 second from now");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed - scheduling restart");
        scheduleServiceRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
