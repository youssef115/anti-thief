package com.youssef.anti_thief.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;


public class AntiTheftDeviceAdmin extends DeviceAdminReceiver {
    
    private static final String TAG = "DeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Device Admin enabled");
        Toast.makeText(context, "Anti-Theft protection activated!", Toast.LENGTH_SHORT).show();
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
}
