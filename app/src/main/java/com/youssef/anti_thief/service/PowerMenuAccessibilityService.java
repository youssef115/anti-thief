package com.youssef.anti_thief.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * V4 - Power Menu Interceptor
 * Detects when power menu appears and triggers fake shutdown + emergency capture
 * INSTANT trigger on Volume+Power combo or power menu detection
 */
public class PowerMenuAccessibilityService extends AccessibilityService {

    private static final String TAG = "PowerMenuService";
    
    // Power menu package names for different Android versions/manufacturers
    private static final String[] POWER_MENU_PACKAGES = {
            "com.android.systemui",
            "android",
            "com.samsung.android.globalactions.presentation",
            "com.miui.securitycenter",
            "com.miui.home",
            "com.miui.globalactions",
            "com.huawei.systemmanager",
            "com.coloros.globalactions",
            "com.oplus.globalactions"
    };
    
    // Keywords that indicate power menu (multiple languages)
    private static final String[] POWER_MENU_KEYWORDS = {
            // English
            "power off", "poweroff", "shutdown", "shut down", "turn off",
            "restart", "reboot", "emergency mode", "power menu",
            // MIUI specific
            "reboot to", "recovery", "fastboot",
            // French
            "eteindre", "éteindre", "redemarrer", "redémarrer", "arreter", "arrêter",
            // Arabic
            "إيقاف", "اغلاق", "إعادة", 
            // Spanish
            "apagar", "reiniciar",
            // German
            "ausschalten", "neustart",
            // Chinese
            "关机", "重启"
    };

    // Static to persist across activity restarts
    private static long lastTriggerTime = 0;
    private static final long TRIGGER_COOLDOWN = 5000; // 5 seconds cooldown (reduced)
    
    // Flag to track if fake shutdown is active
    private static boolean isFakeShutdownActive = false;
    
    // Volume + Power combo detection
    private boolean volumeUpPressed = false;
    private boolean volumeDownPressed = false;
    private boolean powerPressed = false;
    private long comboStartTime = 0;
    private static final long COMBO_TIMEOUT = 500; // 500ms window for combo
    
    private Handler handler = new Handler(Looper.getMainLooper());
    
    public static void setFakeShutdownActive(boolean active) {
        isFakeShutdownActive = active;
        Log.d(TAG, "FakeShutdown active: " + active);
        // Reset cooldown when fake shutdown ends
        if (!active) {
            lastTriggerTime = 0;
        }
    }
    
    public static boolean isFakeShutdownActive() {
        return isFakeShutdownActive;
    }
    
    // Reset flag when service starts (in case it was stuck)
    private void resetState() {
        isFakeShutdownActive = false;
        lastTriggerTime = 0;
        Log.d(TAG, "State reset");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        int eventType = event.getEventType();

        // IMPORTANT: Ignore our own app to prevent loops
        if (packageName.contains("com.youssef.anti_thief")) {
            return;
        }
        
        // If fake shutdown is active, KEEP DISMISSING power menu but don't launch new activity
        if (isFakeShutdownActive) {
            if (isPowerMenuPackage(packageName)) {
                Log.d(TAG, "Power menu appeared during stealth mode - DISMISSING");
                // Aggressively dismiss the power menu
                dismissPowerMenu();
            }
            return;
        }

        // Check if this is from system UI (power menu)
        if (isPowerMenuPackage(packageName)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                // FAST CHECK: Check event text first (instant)
                List<CharSequence> texts = event.getText();
                for (CharSequence text : texts) {
                    if (text != null && containsPowerKeyword(text.toString())) {
                        Log.d(TAG, ">>> INSTANT TRIGGER: " + text);
                        triggerEmergencyProtocol();
                        return;
                    }
                }
                
                // Quick scan in background (don't block)
                quickScanForPowerMenu();
            }
        }
    }
    
    private void dismissPowerMenu() {
        // Press back multiple times to dismiss power menu
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 50);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 100);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 150);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 200);
        Log.d(TAG, "Power menu dismissed");
    }
    
    private void quickScanForPowerMenu() {
        // Run scan in background to not block
        new Thread(() -> {
            if (isFakeShutdownActive) return;
            
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                boolean found = fastScanNode(rootNode, 0);
                rootNode.recycle();
                
                if (found && !isFakeShutdownActive) {
                    handler.post(this::triggerEmergencyProtocol);
                }
            }
        }).start();
    }
    
    private boolean fastScanNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 5) return false; // Reduced depth for speed
        
        // Check text
        CharSequence text = node.getText();
        if (text != null && containsPowerKeyword(text.toString())) {
            Log.d(TAG, ">>> KEYWORD FOUND: " + text);
            return true;
        }
        
        // Check description
        CharSequence desc = node.getContentDescription();
        if (desc != null && containsPowerKeyword(desc.toString())) {
            Log.d(TAG, ">>> KEYWORD FOUND: " + desc);
            return true;
        }
        
        // Check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (fastScanNode(child, depth + 1)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        return false;
    }
    
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        
        // Track Volume + Power combo
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = isDown;
            if (isDown) checkCombo();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = isDown;
            if (isDown) checkCombo();
        } else if (keyCode == KeyEvent.KEYCODE_POWER) {
            powerPressed = isDown;
            if (isDown) {
                Log.d(TAG, "Power button pressed");
                checkCombo();
            }
        }
        
        return false; // Don't consume
    }
    
    private void checkCombo() {
        long now = System.currentTimeMillis();
        
        Log.d(TAG, "checkCombo: vol_up=" + volumeUpPressed + " vol_down=" + volumeDownPressed + " power=" + powerPressed + " fakeActive=" + isFakeShutdownActive);
        
        // Reset if too much time passed
        if (comboStartTime > 0 && (now - comboStartTime) > COMBO_TIMEOUT) {
            resetCombo();
        }
        
        // Start tracking
        if (comboStartTime == 0) {
            comboStartTime = now;
        }
        
        // Check for Volume+Power combo (either volume button + power)
        if (powerPressed && (volumeUpPressed || volumeDownPressed)) {
            Log.d(TAG, ">>> VOLUME + POWER COMBO DETECTED! <<<");
            resetCombo();
            
            if (isFakeShutdownActive) {
                // Already in stealth mode - just dismiss any power menu that appears
                Log.d(TAG, "Already in stealth mode - dismissing power menu");
                dismissPowerMenu();
            } else {
                // Trigger IMMEDIATELY
                Log.d(TAG, "Triggering emergency protocol NOW");
                triggerEmergencyProtocol();
            }
        }
    }
    
    private void resetCombo() {
        volumeUpPressed = false;
        volumeDownPressed = false;
        powerPressed = false;
        comboStartTime = 0;
    }

    private boolean isPowerMenuPackage(String packageName) {
        for (String pkg : POWER_MENU_PACKAGES) {
            if (packageName.contains(pkg)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPowerKeyword(String text) {
        String lowerText = text.toLowerCase();
        for (String keyword : POWER_MENU_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void triggerEmergencyProtocol() {
        // DISABLED FOR NOW - Will be re-enabled later
        Log.d(TAG, "triggerEmergencyProtocol: DISABLED - skipping fake shutdown");
        
        /*
        long now = System.currentTimeMillis();
        long timeSinceLastTrigger = now - lastTriggerTime;
        
        Log.d(TAG, "triggerEmergencyProtocol: timeSinceLastTrigger=" + timeSinceLastTrigger + "ms, cooldown=" + TRIGGER_COOLDOWN + "ms");
        
        if (timeSinceLastTrigger < TRIGGER_COOLDOWN && lastTriggerTime > 0) {
            Log.d(TAG, "Cooldown active, skipping trigger");
            return;
        }
        lastTriggerTime = now;

        Log.d(TAG, "=== TRIGGERING EMERGENCY PROTOCOL ===");

        // 1. Try to dismiss the power menu by pressing back multiple times
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 100);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 200);

        // 2. Launch fake shutdown screen IMMEDIATELY
        Intent fakeShutdownIntent = new Intent(this, FakeShutdownActivity.class);
        fakeShutdownIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                Intent.FLAG_ACTIVITY_NO_ANIMATION);
        fakeShutdownIntent.putExtra("trigger", "power_menu");
        startActivity(fakeShutdownIntent);

        Log.d(TAG, "Fake shutdown launched IMMEDIATELY");
        */
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "=== Power Menu Accessibility Service Connected ===");
        
        // Reset state when service connects (in case flag was stuck)
        resetState();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                     AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 50; // Faster response

        setServiceInfo(info);
        
        Log.d(TAG, "Service ready - Volume+Power combo detection enabled");
    }
}
