package com.youssef.anti_thief.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.youssef.anti_thief.utils.EmergencyCapture;

/**
 * V4 - Enhanced Device Admin with Wrong Password Detection
 * Triggers emergency capture after failed unlock attempts
 */
public class AntiTheftDeviceAdmin extends DeviceAdminReceiver {
    
    private static final String TAG = "DeviceAdmin";
    private static final String PREFS_NAME = "device_admin_prefs";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final int TRIGGER_THRESHOLD = 3; // Trigger after 3 failed attempts
    
    private static long lastTriggerTime = 0;
    private static final long TRIGGER_COOLDOWN = 60000; // 1 minute cooldown

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Device Admin enabled");
        Toast.makeText(context, "Anti-Theft protection activated!", Toast.LENGTH_SHORT).show();
        resetFailedAttempts(context);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.d(TAG, "Device Admin disabled");
        Toast.makeText(context, "Anti-Theft protection deactivated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Warning: Disabling will stop anti-theft protection!";
    }

    /**
     * V4 - Called when wrong password/PIN/pattern is entered
     */
    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        
        int failedAttempts = incrementFailedAttempts(context);
        Log.d(TAG, "Password failed! Total attempts: " + failedAttempts);

        if (failedAttempts >= TRIGGER_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime > TRIGGER_COOLDOWN) {
                lastTriggerTime = now;
                Log.d(TAG, "=== WRONG PASSWORD THRESHOLD REACHED - TRIGGERING EMERGENCY ===");
                
                // Trigger emergency capture
                EmergencyCapture.capture(context, "Wrong Password Attempt (" + failedAttempts + " tries)");
                
                // Reset counter after trigger
                resetFailedAttempts(context);
            } else {
                Log.d(TAG, "Cooldown active, skipping trigger");
            }
        }
    }

    /**
     * Called when correct password is entered
     */
    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Password succeeded, resetting failed attempts");
        resetFailedAttempts(context);
    }

    private int incrementFailedAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
        return attempts;
    }

    private void resetFailedAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply();
    }
}
