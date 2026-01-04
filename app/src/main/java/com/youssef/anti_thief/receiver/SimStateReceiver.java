package com.youssef.anti_thief.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.youssef.anti_thief.service.FakeShutdownActivity;

/**
 * V4 - SIM Card Removal Detection
 * Triggers emergency capture when SIM card is removed
 */
public class SimStateReceiver extends BroadcastReceiver {

    private static final String TAG = "SimStateReceiver";
    private static long lastTriggerTime = 0;
    private static final long TRIGGER_COOLDOWN = 30000; // 30 seconds cooldown

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if ("android.intent.action.SIM_STATE_CHANGED".equals(action)) {
            String simState = intent.getStringExtra("ss");
            Log.d(TAG, "SIM state changed: " + simState);

            // Check for SIM removal states
            if ("ABSENT".equals(simState) || "NOT_READY".equals(simState)) {
                handleSimRemoval(context);
            }
        }
    }

    private void handleSimRemoval(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < TRIGGER_COOLDOWN) {
            Log.d(TAG, "Cooldown active, skipping SIM removal trigger");
            return;
        }
        lastTriggerTime = now;

        Log.d(TAG, "=== SIM CARD REMOVED - TRIGGERING EMERGENCY ===");

        // Launch fake shutdown with SIM removal trigger
        Intent fakeShutdownIntent = new Intent(context, FakeShutdownActivity.class);
        fakeShutdownIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        fakeShutdownIntent.putExtra("trigger", "sim_removed");
        context.startActivity(fakeShutdownIntent);
    }
}
