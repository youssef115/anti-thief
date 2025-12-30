package com.youssef.anti_thief.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.youssef.anti_thief.service.TrackingService;


public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);


        startTrackingService(context);
    }

    private void startTrackingService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, TrackingService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            Log.d(TAG, "TrackingService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TrackingService", e);
        }
    }
}
