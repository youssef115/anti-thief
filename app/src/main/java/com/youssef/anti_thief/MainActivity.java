package com.youssef.anti_thief;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.youssef.anti_thief.config.Config;
import com.youssef.anti_thief.config.ConfigManager;
import com.youssef.anti_thief.receiver.AntiTheftDeviceAdmin;
import com.youssef.anti_thief.service.TrackingService;
import com.youssef.anti_thief.worker.ServiceKeepAliveWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int DEVICE_ADMIN_REQUEST_CODE = 1002;
    
    private TextView statusText;
    private Button startButton;
    private Button permissionButton;
    private Button deviceAdminButton;
    private Button configButton;
    private Button lockScreenProtectionButton;
    private Button accessibilityButton;
    private Button pinShutdownButton;
    
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private Button miuiSettingsButton;
    
    private ConfigManager configManager;
    
    private boolean isLockScreenProtectionEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Config.init(this);
        configManager = new ConfigManager(this);
        
        if (!configManager.isSetupComplete()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);

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
        
        configButton = findViewById(R.id.configButton);
        configButton.setOnClickListener(v -> openSetup());
        
        lockScreenProtectionButton = findViewById(R.id.lockScreenProtectionButton);
        lockScreenProtectionButton.setOnClickListener(v -> toggleLockScreenProtection());
        
        // V4: Accessibility Service button
        accessibilityButton = findViewById(R.id.accessibilityButton);
        accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        
        // V4: PIN Shutdown Guide button
        pinShutdownButton = findViewById(R.id.pinShutdownButton);
        pinShutdownButton.setOnClickListener(v -> showPinShutdownGuide());

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
        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled();
        
        startButton.setEnabled(allGranted);
        
        if (isDeviceAdmin) {
            deviceAdminButton.setText("Device Admin Active (Tap to re-enable)");
            deviceAdminButton.setEnabled(true);
        } else {
            deviceAdminButton.setText("Enable Device Admin (Required for Camera)");
            deviceAdminButton.setEnabled(true);
        }
        
        // V4: Update accessibility button
        if (isAccessibilityEnabled) {
            accessibilityButton.setText("Power Menu Protection: ON");
        } else {
            accessibilityButton.setText("Enable Power Menu Protection");
        }
        
        updateLockScreenProtectionButton(isDeviceAdmin);
        
        StringBuilder status = new StringBuilder();
        if (allGranted) {
            status.append("Permissions: OK\n");
        } else {
            status.append("Permissions: Missing\n");
        }
        
        if (isDeviceAdmin) {
            status.append("Device Admin: Active\n");
        } else {
            status.append("Device Admin: Not active\n");
        }
        
        if (isAccessibilityEnabled) {
            status.append("Power Menu Protection: Active\n");
        } else {
            status.append("Power Menu Protection: Not active\n");
        }
        
        if (allGranted && isDeviceAdmin && isAccessibilityEnabled) {
            status.append("\nFull protection enabled!");
            permissionButton.setText("Permissions OK");
        } else if (allGranted && isDeviceAdmin) {
            status.append("\nEnable Power Menu Protection for full security.");
            permissionButton.setText("Permissions OK");
        } else if (allGranted) {
            status.append("\nEnable Device Admin for camera capture.");
            permissionButton.setText("Permissions OK");
        } else {
            status.append("\nPlease grant all permissions.");
            permissionButton.setText("Grant Permissions");
        }
        
        statusText.setText(status.toString());
    }
    
    // V4: Check if accessibility service is enabled
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().contains(getPackageName()) && 
                service.getId().contains("PowerMenuAccessibilityService")) {
                return true;
            }
        }
        return false;
    }
    
    // V4: Open accessibility settings
    private void openAccessibilitySettings() {
        new AlertDialog.Builder(this)
            .setTitle("Enable Power Menu Protection")
            .setMessage("This feature intercepts shutdown attempts and triggers emergency capture.\n\n" +
                    "Steps:\n" +
                    "1. Find 'anti-thief' in the list\n" +
                    "2. Enable the service\n" +
                    "3. Confirm the permission\n\n" +
                    "This allows the app to detect when someone tries to power off your device.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // V4: Show PIN shutdown guide
    private void showPinShutdownGuide() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String guide;
        
        if (manufacturer.contains("samsung")) {
            guide = "Samsung devices:\n\n" +
                    "1. Go to Settings > Lock Screen\n" +
                    "2. Find 'Secure lock settings'\n" +
                    "3. Enable 'Lock network and security'\n\n" +
                    "This requires PIN/pattern to access power menu options.";
        } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            guide = "Xiaomi/MIUI devices:\n\n" +
                    "1. Go to Settings > Passwords & security\n" +
                    "2. Find 'Privacy' section\n" +
                    "3. Enable 'Lock screen password required to power off'\n\n" +
                    "Or:\n" +
                    "Settings > Lock screen > Advanced settings > Power off requires password";
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            guide = "Huawei/Honor devices:\n\n" +
                    "1. Go to Settings > Security\n" +
                    "2. Find 'More settings'\n" +
                    "3. Enable 'Power button requires password'\n\n" +
                    "This prevents shutdown without unlocking.";
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            guide = "OPPO/Realme/OnePlus devices:\n\n" +
                    "1. Go to Settings > Password & security\n" +
                    "2. Find 'System security'\n" +
                    "3. Enable 'Power off requires password'\n\n" +
                    "This adds PIN protection to power menu.";
        } else {
            guide = "General Android:\n\n" +
                    "1. Go to Settings > Security\n" +
                    "2. Look for 'Lock screen' or 'Screen lock'\n" +
                    "3. Find options like:\n" +
                    "   - 'Power button instantly locks'\n" +
                    "   - 'Lock network and security'\n" +
                    "   - 'Require password to power off'\n\n" +
                    "Note: Not all devices support this feature.\n" +
                    "The Power Menu Protection (Accessibility) provides an alternative.";
        }
        
        new AlertDialog.Builder(this)
            .setTitle("PIN-Protected Shutdown Guide")
            .setMessage(guide)
            .setPositiveButton("Open Security Settings", (dialog, which) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }
    
    private void updateLockScreenProtectionButton(boolean isDeviceAdmin) {
        if (!isDeviceAdmin) {
            lockScreenProtectionButton.setText("Lock Screen Protection (Enable Device Admin first)");
            lockScreenProtectionButton.setEnabled(false);
            return;
        }
        
        lockScreenProtectionButton.setEnabled(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int disabledFeatures = devicePolicyManager.getKeyguardDisabledFeatures(adminComponent);
            isLockScreenProtectionEnabled = (disabledFeatures & DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL) != 0;
        }
        
        if (isLockScreenProtectionEnabled) {
            lockScreenProtectionButton.setText("Lock Screen Protection: ON (Tap to disable)");
        } else {
            lockScreenProtectionButton.setText("Lock Screen Protection: OFF (Tap to enable)");
        }
    }
    
    private void toggleLockScreenProtection() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Toast.makeText(this, "Please enable Device Admin first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (isLockScreenProtectionEnabled) {
                    devicePolicyManager.setKeyguardDisabledFeatures(adminComponent, 
                            DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE);
                    isLockScreenProtectionEnabled = false;
                    Toast.makeText(this, "Lock screen protection disabled", Toast.LENGTH_SHORT).show();
                } else {
                    devicePolicyManager.setKeyguardDisabledFeatures(adminComponent,
                            DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL);
                    isLockScreenProtectionEnabled = true;
                    Toast.makeText(this, "Lock screen protection enabled!\nNotification panel blocked on lock screen.", Toast.LENGTH_LONG).show();
                }
                updateLockScreenProtectionButton(true);
            } catch (SecurityException e) {
                showDeviceAdminResetDialog();
            }
        } else {
            Toast.makeText(this, "Lock screen protection requires Android 5.0+", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDeviceAdminResetDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Device Admin Update Required")
            .setMessage("To enable Lock Screen Protection, you need to:\n\n" +
                    "1. Disable Device Admin\n" +
                    "2. Re-enable Device Admin\n\n" +
                    "This will grant the new lock screen policy.\n\n" +
                    "Tap 'Open Settings' to disable Device Admin, then come back and enable it again.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.DeviceAdminSettings"));
                try {
                    startActivity(intent);
                    Toast.makeText(this, "Disable 'Anti-Thief' then come back to re-enable", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Intent fallback = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    startActivity(fallback);
                    Toast.makeText(this, "Go to Device Administrators and disable Anti-Thief", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Anti-Theft needs Device Admin to:\n" +
                "- Capture photos when device is stolen\n" +
                "- Block lock screen notification panel\n" +
                "- Detect wrong password attempts\n" +
                "- Bypass MIUI security restrictions");
        startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE);
    }

    private void openMiuiSettings() {
        try {
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
                Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                intent.setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
                intent.putExtra("extra_pkgname", getPackageName());
                startActivity(intent);
            } catch (Exception e2) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, 
                    "Go to: Other permissions > Enable all camera and display permissions", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
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
        boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean backgroundLocation = true;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean batteryOptimized = pm.isIgnoringBatteryOptimizations(getPackageName());

        boolean canOverlay = Settings.canDrawOverlays(this);

        return location && camera && backgroundLocation && batteryOptimized && canOverlay;
    }

    private void requestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            requestSpecialPermissions();
        }
    }

    private void requestSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_CODE + 1);
                return;
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show();
            return;
        }

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
            requestSpecialPermissions();
        } else if (requestCode == PERMISSION_REQUEST_CODE + 1) {
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

        scheduleKeepAliveWorker();

        Toast.makeText(this, "Tracking service started!", Toast.LENGTH_SHORT).show();
        statusText.setText("Service is running!\nYou can close this app now.");
    }

    private void scheduleKeepAliveWorker() {
        PeriodicWorkRequest keepAliveRequest = new PeriodicWorkRequest.Builder(
                ServiceKeepAliveWorker.class,
                15, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "service_keep_alive",
                ExistingPeriodicWorkPolicy.KEEP,
                keepAliveRequest
        );

        android.util.Log.d("MainActivity", "KeepAlive worker scheduled");
    }
}
