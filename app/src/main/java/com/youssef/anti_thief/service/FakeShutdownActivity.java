package com.youssef.anti_thief.service;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.youssef.anti_thief.utils.EmergencyCapture;

/**
 * V4 - Fake Shutdown Screen
 * Shows realistic shutdown animation, then stays black while capturing data
 * Secret pattern to exit: Volume Up x2, Volume Down x2 (within 2 seconds)
 */
public class FakeShutdownActivity extends Activity {

    private static final String TAG = "FakeShutdown";
    
    // Secret exit pattern: UP, UP, DOWN, DOWN
    private static final int KEY_UP = 0;
    private static final int KEY_DOWN = 1;
    
    private int[] expectedPattern = {KEY_UP, KEY_UP, KEY_DOWN, KEY_DOWN};
    private int currentPatternIndex = 0;
    private long patternStartTime = 0;
    private static final long PATTERN_TIMEOUT = 2000; // 2 seconds to complete pattern

    private FrameLayout rootLayout;
    private TextView shutdownText;
    private ProgressBar progressBar;
    private View blackOverlay;
    
    private PowerManager.WakeLock wakeLock;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInStealthMode = false;
    private String triggerType = "shutdown_attempt";

    private int screenHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== FAKE SHUTDOWN STARTED ===");
        
        // Tell accessibility service we're active (prevent loops)
        PowerMenuAccessibilityService.setFakeShutdownActive(true);

        // Get trigger type
        if (getIntent() != null && getIntent().hasExtra("trigger")) {
            triggerType = getIntent().getStringExtra("trigger");
        }
        Log.d(TAG, "Trigger type: " + triggerType);

        // Acquire wake lock to keep device awake
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "antithief:fakeshutdown"
        );
        wakeLock.acquire(300000); // 5 minutes max

        // Setup fullscreen immersive
        setupFullscreen();
        
        // Get screen height for progress bar positioning
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Create UI programmatically
        createUI();

        // Set phone to silent mode
        setSilentMode();

        // Start the fake shutdown sequence
        startFakeShutdownSequence();
    }

    private void setupFullscreen() {
        // Make activity fullscreen and show over lock screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        // Hide system UI
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void createUI() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Shutdown text
        shutdownText = new TextView(this);
        shutdownText.setText("Shutting down...");
        shutdownText.setTextColor(Color.WHITE);
        shutdownText.setTextSize(24);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = android.view.Gravity.CENTER;
        shutdownText.setLayoutParams(textParams);
        rootLayout.addView(shutdownText);

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                600,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = screenHeight / 2 + 100;
        progressBar.setLayoutParams(progressParams);
        rootLayout.addView(progressBar);

        // Black overlay (initially invisible)
        blackOverlay = new View(this);
        blackOverlay.setBackgroundColor(Color.BLACK);
        blackOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        blackOverlay.setVisibility(View.GONE);
        rootLayout.addView(blackOverlay);

        setContentView(rootLayout);
    }

    private void setSilentMode() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            
            // Also mute all streams
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            
            Log.d(TAG, "Phone set to silent mode");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set silent mode", e);
        }
    }

    private void startFakeShutdownSequence() {
        Log.d(TAG, "Starting fake shutdown animation");

        // Vibrate like real shutdown
        vibrateShutdown();

        // Phase 1: Show "Shutting down..." for 2 seconds
        handler.postDelayed(() -> {
            shutdownText.setText("Shutting down...");
        }, 500);

        // Phase 2: Fade out text and progress
        handler.postDelayed(() -> {
            AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
            fadeOut.setDuration(1000);
            fadeOut.setFillAfter(true);
            
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                
                @Override
                public void onAnimationEnd(Animation animation) {
                    enterStealthMode();
                }
                
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            shutdownText.startAnimation(fadeOut);
            progressBar.startAnimation(fadeOut);
        }, 2500);

        // Start emergency capture immediately in background
        handler.postDelayed(() -> {
            triggerEmergencyCapture();
        }, 500);
    }

    private void vibrateShutdown() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(200);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration failed", e);
        }
    }

    private void enterStealthMode() {
        Log.d(TAG, "=== ENTERING STEALTH MODE ===");
        Log.d(TAG, "Exit pattern: Volume UP x2, Volume DOWN x2 (within 2 seconds)");
        isInStealthMode = true;

        // Hide everything, show black screen
        shutdownText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        blackOverlay.setVisibility(View.VISIBLE);

        // Reduce screen brightness to minimum
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 0.01f; // Almost off but screen is on
        getWindow().setAttributes(params);

        Log.d(TAG, "Stealth mode active - screen appears off");
    }

    private void triggerEmergencyCapture() {
        Log.d(TAG, "=== TRIGGERING EMERGENCY CAPTURE ===");
        
        String alertType;
        switch (triggerType) {
            case "power_menu":
                alertType = "Shutdown Attempt Detected";
                break;
            case "sim_removed":
                alertType = "SIM Card Removed";
                break;
            case "wrong_password":
                alertType = "Wrong Password Attempt";
                break;
            default:
                alertType = "Security Alert";
        }

        // Use EmergencyCapture utility
        EmergencyCapture.capture(this, alertType);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isInStealthMode) {
            return super.onKeyDown(keyCode, event);
        }

        // Handle volume buttons for secret pattern
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handleVolumeKey(keyCode);
            return true; // Consume the event
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isInStealthMode && event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                handleVolumeKey(keyCode);
                return true; // Consume the event
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void handleVolumeKey(int keyCode) {
        int pressedKey = (keyCode == KeyEvent.KEYCODE_VOLUME_UP) ? KEY_UP : KEY_DOWN;
        String keyName = (pressedKey == KEY_UP) ? "UP" : "DOWN";
        
        Log.d(TAG, "Volume " + keyName + " pressed");

        long now = System.currentTimeMillis();

        // Check if pattern timed out
        if (currentPatternIndex > 0 && (now - patternStartTime) > PATTERN_TIMEOUT) {
            Log.d(TAG, "Pattern timeout, resetting");
            resetPattern();
        }

        // Check if this is the expected key
        if (pressedKey == expectedPattern[currentPatternIndex]) {
            if (currentPatternIndex == 0) {
                patternStartTime = now;
            }
            
            currentPatternIndex++;
            Log.d(TAG, ">>> CORRECT! Progress: " + currentPatternIndex + "/4");

            if (currentPatternIndex == expectedPattern.length) {
                // Pattern complete!
                Log.d(TAG, "=== SECRET PATTERN COMPLETE - EXITING STEALTH MODE ===");
                exitStealthMode();
            }
        } else {
            // Wrong key, reset
            Log.d(TAG, "Wrong key, resetting pattern");
            resetPattern();
        }
    }

    private void resetPattern() {
        currentPatternIndex = 0;
        patternStartTime = 0;
    }

    private void exitStealthMode() {
        Log.d(TAG, "Exiting stealth mode");
        
        // Tell accessibility service we're done (allow future triggers after cooldown)
        PowerMenuAccessibilityService.setFakeShutdownActive(false);
        
        // Restore brightness
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Finish activity
        finish();
    }

    @Override
    public void onBackPressed() {
        // Block back button in stealth mode
        if (isInStealthMode) {
            Log.d(TAG, "Back button blocked in stealth mode");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Make sure flag is cleared
        PowerMenuAccessibilityService.setFakeShutdownActive(false);
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        handler.removeCallbacksAndMessages(null);
    }
}
