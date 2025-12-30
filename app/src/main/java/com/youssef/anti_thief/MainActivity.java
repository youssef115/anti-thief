package com.youssef.anti_thief;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.youssef.anti_thief.receiver.AntiTheftDeviceAdmin;
import com.youssef.anti_thief.service.TrackingService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int DEVICE_ADMIN_REQUEST_CODE = 1002;
    
    private TextView statusText;
    private Button startButton;
    private Button permissionButton;
    private Button deviceAdminButton;
    
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private Button miuiSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Device Admin
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AntiTheftDeviceAdmin.class);

        statusText = findViewById(R.id.statusText);
        startButton = findViewById(R.id.startButton);
        permissionButton = findViewById(R.id.permissionButton);
        deviceAdminButton = findViewById(R.id.deviceAdminButton);

        permissionButton.setOnClickListener(v -> requestAllPermissions());
        startButton.setOnClickListener(v -> startTrackingService());
        deviceAdminButton.setOnClickListener(v -> requestDeviceAdmin());
        
        miuiSettingsButton = findViewById(R.id.miuiSettingsButton);
        miuiSettingsButton.setOnClickListener(v -> openMiuiSettings());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean allGranted = checkAllPermissions();
        boolean isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent);
        
        startButton.setEnabled(allGranted);
        
        // Update Device Admin button
        if (isDeviceAdmin) {
            deviceAdminButton.setText("✓ Device Admin Active");
            deviceAdminButton.setEnabled(false);
        } else {
            deviceAdminButton.setText("Enable Device Admin (Required for Camera)");
            deviceAdminButton.setEnabled(true);
        }
        
        if (allGranted && isDeviceAdmin) {
            statusText.setText("✓ All permissions granted!\n✓ Device Admin active!\nReady to start tracking with camera capture.");
            permissionButton.setText("Permissions OK");
        } else if (allGranted) {
            statusText.setText("✓ Permissions granted\n⚠ Device Admin not active\n\nEnable Device Admin for camera capture on MIUI devices.");
            permissionButton.setText("Permissions OK");
        } else {
            statusText.setText("⚠ Some permissions are missing.\nPlease grant all permissions for the app to work properly.");
            permissionButton.setText("Grant Permissions");
        }
    }

    private boolean isDeviceAdminActive() {
        return devicePolicyManager.isAdminActive(adminComponent);
    }

    private void requestDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Anti-Theft needs Device Admin to capture photos when your device is stolen. " +
                    "This is required to bypass MIUI security restrictions.");
            startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE);
        }
    }

    /**
     * Opens MIUI-specific app settings where user can enable:
     * - Autostart
     * - Background activity
     * - Lock screen display
     */
    private void openMiuiSettings() {
        try {
            // Try MIUI Security app permissions
            Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", getPackageName());
            startActivity(intent);
            Toast.makeText(this, 
                "Enable: Autostart, Lock screen display, Background pop-ups", 
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                // Try alternative MIUI settings
                Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                intent.setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
                intent.putExtra("extra_pkgname", getPackageName());
                startActivity(intent);
            } catch (Exception e2) {
                // Fallback to standard app settings
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, 
                    "Go to: Other permissions > Enable all camera and display permissions", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Device Admin enabled! Camera capture will now work.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Device Admin is required for camera capture on MIUI", Toast.LENGTH_LONG).show();
            }
            updateUI();
        }
    }

    private boolean checkAllPermissions() {
        // Check runtime permissions
        boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean backgroundLocation = true;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        // Check battery optimization
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean batteryOptimized = pm.isIgnoringBatteryOptimizations(getPackageName());

        // Check overlay permission
        boolean canOverlay = Settings.canDrawOverlays(this);

        return location && camera && backgroundLocation && batteryOptimized && canOverlay;
    }

    private void requestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            // All basic permissions granted, now request special permissions
            requestSpecialPermissions();
        }
    }

    private void requestSpecialPermissions() {
        // Request background location (must be done separately on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_CODE + 1);
                return;
            }
        }

        // Request overlay permission (critical for Xiaomi)
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show();
            return;
        }

        // Request battery optimization exemption
        requestBatteryOptimization();
    }

    private void requestBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // After basic permissions, request special ones
            requestSpecialPermissions();
        } else if (requestCode == PERMISSION_REQUEST_CODE + 1) {
            // After background location, request battery optimization
            requestBatteryOptimization();
        }
        
        updateUI();
    }

    private void startTrackingService() {
        if (!checkAllPermissions()) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, TrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Tracking service started!", Toast.LENGTH_SHORT).show();
        statusText.setText("✓ Service is running!\nYou can close this app now.");
    }
}
