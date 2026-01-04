package com.youssef.anti_thief.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.youssef.anti_thief.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * V4 - Emergency Capture Utility
 * Captures photos and location data quickly during emergency situations
 * Used by FakeShutdownActivity, SIM removal, wrong password detection
 */
public class EmergencyCapture {

    private static final String TAG = "EmergencyCapture";
    private static final int PHOTOS_PER_CAMERA = 4;
    private static final long PHOTO_INTERVAL_MS = 50;

    public static void capture(Context context, String alertType) {
        Log.d(TAG, "=== EMERGENCY CAPTURE STARTED: " + alertType + " ===");

        new Thread(() -> {
            try {
                // Initialize config
                Config.init(context);

                // Capture photos
                List<String> photoPaths = capturePhotos(context);
                Log.d(TAG, "Captured " + photoPaths.size() + " photos");

                // Get current location
                Location currentLocation = getCurrentLocation(context);
                if (currentLocation != null) {
                    Log.d(TAG, "Current location: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                }

                // Create ZIP and send email
                if (!photoPaths.isEmpty()) {
                    String zipPath = ZipCreatorEmergency.createEmergencyZip(context, photoPaths, alertType, currentLocation);
                    
                    if (zipPath != null) {
                        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                        String subject = "[ALERT] " + alertType + " - " + timestamp;
                        
                        Log.d(TAG, "Sending emergency email: " + subject);
                        GMailSender.sendEmailWithZip(zipPath, subject);

                        // Cleanup photos
                        for (String path : photoPaths) {
                            try {
                                new File(path).delete();
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to delete: " + path);
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "No photos captured, sending location-only alert");
                    sendLocationOnlyAlert(context, alertType, currentLocation);
                }

                Log.d(TAG, "=== EMERGENCY CAPTURE COMPLETE ===");

            } catch (Exception e) {
                Log.e(TAG, "Emergency capture failed", e);
            }
        }).start();
    }

    private static List<String> capturePhotos(Context context) {
        List<String> allPhotos = new ArrayList<>();
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
            return allPhotos;
        }

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        
        // Capture from back camera
        List<String> backPhotos = captureFromCamera(context, cameraManager, false);
        allPhotos.addAll(backPhotos);
        
        // Small delay between cameras
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        
        // Capture from front camera
        List<String> frontPhotos = captureFromCamera(context, cameraManager, true);
        allPhotos.addAll(frontPhotos);

        return allPhotos;
    }

    private static List<String> captureFromCamera(Context context, CameraManager cameraManager, boolean isFront) {
        List<String> photos = new ArrayList<>();
        String cameraName = isFront ? "FRONT" : "BACK";
        
        HandlerThread handlerThread = new HandlerThread("EmergencyCamera");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        CountDownLatch latch = new CountDownLatch(1);
        final int[] photoCount = {0};

        try {
            String cameraId = getCameraId(cameraManager, isFront);
            if (cameraId == null) {
                Log.e(TAG, cameraName + " camera not found");
                return photos;
            }

            ImageReader imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, PHOTOS_PER_CAMERA + 1);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        String path = saveImage(context, bytes, isFront, photoCount[0]);
                        if (path != null) {
                            synchronized (photos) {
                                photos.add(path);
                            }
                            Log.d(TAG, cameraName + " photo " + (photoCount[0] + 1) + " saved");
                        }
                        photoCount[0]++;
                        
                        if (photoCount[0] >= PHOTOS_PER_CAMERA) {
                            latch.countDown();
                        }
                    }
                } finally {
                    if (image != null) image.close();
                }
            }, handler);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    CameraDevice camera;
                    CameraCaptureSession session;

                    @Override
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        camera = cameraDevice;
                        try {
                            SurfaceTexture dummyTexture = new SurfaceTexture(0);
                            dummyTexture.setDefaultBufferSize(640, 480);
                            Surface dummySurface = new Surface(dummyTexture);

                            camera.createCaptureSession(
                                    Arrays.asList(imageReader.getSurface(), dummySurface),
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                                            session = captureSession;
                                            // Take photos rapidly
                                            for (int i = 0; i < PHOTOS_PER_CAMERA; i++) {
                                                final int photoIndex = i;
                                                handler.postDelayed(() -> {
                                                    try {
                                                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                                        builder.addTarget(imageReader.getSurface());
                                                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                                        session.capture(builder.build(), null, handler);
                                                    } catch (Exception e) {
                                                        Log.e(TAG, "Capture error", e);
                                                    }
                                                }, i * PHOTO_INTERVAL_MS + 200);
                                            }
                                        }

                                        @Override
                                        public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                                            Log.e(TAG, "Session config failed");
                                            latch.countDown();
                                        }
                                    },
                                    handler
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Session creation error", e);
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        cameraDevice.close();
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int error) {
                        Log.e(TAG, "Camera error: " + error);
                        cameraDevice.close();
                        latch.countDown();
                    }
                }, handler);
            }

            // Wait for capture to complete (max 5 seconds)
            latch.await(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            Log.e(TAG, "Camera capture error", e);
        } finally {
            handlerThread.quitSafely();
        }

        return photos;
    }

    private static String getCameraId(CameraManager cameraManager, boolean isFront) throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                if (isFront && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId;
                } else if (!isFront && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        }
        return null;
    }

    private static String saveImage(Context context, byte[] bytes, boolean isFront, int index) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "emergency_" + (isFront ? "front_" : "back_") + timestamp + "_" + (index + 1) + ".jpg";
        File photoFile = new File(context.getExternalFilesDir(null), fileName);

        try (FileOutputStream fos = new FileOutputStream(photoFile)) {
            fos.write(bytes);
            return photoFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    private static Location getCurrentLocation(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(context);
            CountDownLatch latch = new CountDownLatch(1);
            final Location[] result = {null};

            CancellationTokenSource cts = new CancellationTokenSource();
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(location -> {
                        result[0] = location;
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Location failed", e);
                        latch.countDown();
                    });

            latch.await(5, TimeUnit.SECONDS);
            return result[0];

        } catch (Exception e) {
            Log.e(TAG, "Error getting location", e);
            return null;
        }
    }

    private static void sendLocationOnlyAlert(Context context, String alertType, Location location) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String subject = "[ALERT] " + alertType + " - " + timestamp;
        
        StringBuilder body = new StringBuilder();
        body.append("Security Alert: ").append(alertType).append("\n\n");
        body.append("Time: ").append(timestamp).append("\n");
        body.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n\n");
        
        if (location != null) {
            body.append("Current Location:\n");
            body.append("Latitude: ").append(location.getLatitude()).append("\n");
            body.append("Longitude: ").append(location.getLongitude()).append("\n");
            body.append("Google Maps: https://maps.google.com/?q=")
                .append(location.getLatitude()).append(",").append(location.getLongitude()).append("\n");
        } else {
            body.append("Location: Unable to determine\n");
        }

        GMailSender.sendEmail(subject, body.toString());
    }
}
