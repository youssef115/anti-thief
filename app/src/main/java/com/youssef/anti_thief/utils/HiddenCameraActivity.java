package com.youssef.anti_thief.utils;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.youssef.anti_thief.R;

public class HiddenCameraActivity extends AppCompatActivity {

    private static final String TAG = "HiddenCamera";
    private ServiceCameraCapture cameraCapture;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "=== onCreate START ===");
        super.onCreate(savedInstanceState);

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "antithief:camera"
            );
            wakeLock.acquire(30000);

            setupLockScreenFlags();

            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            setContentView(R.layout.activity_hidden);

            Log.d(TAG, "Layout set, checking camera permission");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Camera permission not granted!");
                finish();
                return;
            }

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(2);

            Log.d(TAG, "Starting camera capture in 500ms...");

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "Timer fired, starting ServiceCameraCapture");
                startCameraCapture();
            }, 500);

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            releaseWakeLock();
            finish();
        }
    }

    private void setupLockScreenFlags() {
        Log.d(TAG, "Setting up lock screen flags");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        }
    }

    private void startCameraCapture() {
        Log.d(TAG, "=== startCameraCapture using ServiceCameraCapture ===");

        cameraCapture = new ServiceCameraCapture(this);
        cameraCapture.capturePhotos(new ServiceCameraCapture.CaptureCallback() {
            @Override
            public void onCaptureComplete(String backPath, String frontPath) {
                Log.d(TAG, "Capture complete! back=" + backPath + ", front=" + frontPath);
                finishActivity();
            }

            @Override
            public void onCaptureFailed(String error) {
                Log.e(TAG, "Capture failed: " + error);
                finishActivity();
            }
        });
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }
    }

    private void finishActivity() {
        Log.d(TAG, "Finishing HiddenCameraActivity");
        releaseWakeLock();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        releaseWakeLock();
        super.onDestroy();
    }
}
