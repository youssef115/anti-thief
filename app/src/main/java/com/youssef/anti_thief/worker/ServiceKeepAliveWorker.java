package com.youssef.anti_thief.worker;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.youssef.anti_thief.service.TrackingService;

public class ServiceKeepAliveWorker extends Worker {

    private static final String TAG = "ServiceKeepAliveWorker";

    public ServiceKeepAliveWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "KeepAlive worker running - checking service status");

        try {
            Context context = getApplicationContext();

            Intent serviceIntent = new Intent(context, TrackingService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "TrackingService start requested");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start service", e);
            return Result.retry();
        }
    }
}
