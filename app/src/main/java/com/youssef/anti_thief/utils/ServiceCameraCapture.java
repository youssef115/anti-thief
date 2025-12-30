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
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ServiceCameraCapture {

    private static final String TAG = "ServiceCameraCapture";
    private static final int PHOTOS_PER_CAMERA = 4;
    private static final long PHOTO_INTERVAL_MS = 50;

    private final Context context;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private CameraCaptureSession captureSession;
    
    private List<String> backPhotoPaths = new ArrayList<>();
    private List<String> frontPhotoPaths = new ArrayList<>();
    private int currentPhotoCount = 0;
    private boolean isCapturing = false;
    private CaptureCallback callback;

    public interface CaptureCallback {
        void onCaptureComplete(String backPath, String frontPath);
        void onCaptureFailed(String error);
    }

    public ServiceCameraCapture(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void capturePhotos(CaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Already capturing");
            return;
        }
        
        this.callback = callback;
        this.isCapturing = true;
        this.backPhotoPaths.clear();
        this.frontPhotoPaths.clear();
        this.currentPhotoCount = 0;

        Log.d(TAG, "=== STARTING SERVICE-BASED CAMERA CAPTURE ===");
        Log.d(TAG, "Will capture " + PHOTOS_PER_CAMERA + " photos per camera with " + PHOTO_INTERVAL_MS + "ms interval");
        
        startBackgroundThread();
        

        captureFromCamera(false);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void captureFromCamera(boolean isFront) {
        String cameraName = isFront ? "FRONT" : "BACK";
        Log.d(TAG, "Starting capture from " + cameraName + " camera");
        currentPhotoCount = 0;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
            finishCapture("Camera permission not granted");
            return;
        }

        try {
            String cameraId = getCameraId(isFront);
            if (cameraId == null) {
                Log.e(TAG, cameraName + " camera not found");
                if (!isFront) {

                    backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                } else {
                    finishCapture(null);
                }
                return;
            }

            Log.d(TAG, "Opening camera: " + cameraId);
            
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, cameraName + " camera opened");
                    cameraDevice = camera;
                    createCaptureSession(isFront);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.e(TAG, cameraName + " camera disconnected");
                    camera.close();
                    if (!isFront) {
                        backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                    } else {
                        finishCapture(null);
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, cameraName + " camera error: " + error);
                    camera.close();
                    if (!isFront) {
                        backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                    } else {
                        finishCapture(null);
                    }
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
            if (!isFront) {
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
            } else {
                finishCapture(e.getMessage());
            }
        }
    }

    private String getCameraId(boolean isFront) throws CameraAccessException {
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

    private void createCaptureSession(boolean isFront) {
        try {

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, PHOTOS_PER_CAMERA + 1);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        
                        String path = saveImage(bytes, isFront, currentPhotoCount);
                        if (path != null) {
                            if (isFront) {
                                frontPhotoPaths.add(path);
                            } else {
                                backPhotoPaths.add(path);
                            }
                            Log.d(TAG, (isFront ? "Front" : "Back") + " photo " + (currentPhotoCount + 1) + " saved: " + path);
                        }
                        currentPhotoCount++;
                    }
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
                

                if (currentPhotoCount < PHOTOS_PER_CAMERA) {

                    backgroundHandler.postDelayed(() -> takePicture(isFront), PHOTO_INTERVAL_MS);
                } else {

                    closeCamera();
                    if (!isFront) {

                        backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                    } else {

                        finishCapture(null);
                    }
                }
            }, backgroundHandler);


            SurfaceTexture dummyTexture = new SurfaceTexture(0);
            dummyTexture.setDefaultBufferSize(640, 480);
            Surface dummySurface = new Surface(dummyTexture);

            cameraDevice.createCaptureSession(
                    Arrays.asList(imageReader.getSurface(), dummySurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "Capture session configured");
                            captureSession = session;
                            

                            backgroundHandler.postDelayed(() -> takePicture(isFront), 300);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                            closeCamera();
                            if (!isFront) {
                                backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                            } else {
                                finishCapture(null);
                            }
                        }
                    },
                    backgroundHandler
            );

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating capture session", e);
            closeCamera();
            if (!isFront) {
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
            } else {
                finishCapture(e.getMessage());
            }
        }
    }

    private void takePicture(boolean isFront) {
        try {
            if (cameraDevice == null || captureSession == null) {
                Log.e(TAG, "Camera not ready");
                return;
            }

            Log.d(TAG, "Taking picture " + (currentPhotoCount + 1) + "/" + PHOTOS_PER_CAMERA + ": " + (isFront ? "FRONT" : "BACK"));

            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            

            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, 
                                               @NonNull CaptureRequest request, 
                                               @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "Capture completed: " + (isFront ? "FRONT" : "BACK") + " #" + (currentPhotoCount + 1));
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error taking picture", e);
            closeCamera();
            if (!isFront) {
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
            } else {
                finishCapture(e.getMessage());
            }
        }
    }

    private String saveImage(byte[] bytes, boolean isFront, int photoIndex) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = (isFront ? "front_" : "back_") + timestamp + "_" + (photoIndex + 1) + ".jpg";
        File photoFile = new File(context.getExternalFilesDir(null), fileName);

        try (FileOutputStream fos = new FileOutputStream(photoFile)) {
            fos.write(bytes);
            Log.d(TAG, "Image saved: " + photoFile.getAbsolutePath());
            return photoFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing session", e);
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void finishCapture(String error) {
        Log.d(TAG, "=== finishCapture START ===");
        Log.d(TAG, "Back photos: " + backPhotoPaths.size());
        Log.d(TAG, "Front photos: " + frontPhotoPaths.size());


        final List<String> allPhotoPaths = new ArrayList<>();
        allPhotoPaths.addAll(backPhotoPaths);
        allPhotoPaths.addAll(frontPhotoPaths);

        closeCamera();
        isCapturing = false;


        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }


        new Thread(() -> {
            if (!allPhotoPaths.isEmpty()) {
                Log.d(TAG, ">>> Creating encrypted ZIP with " + allPhotoPaths.size() + " photos + 24h locations from backend");

                String zipPath = ZipCreator.createSecurityZip(context, allPhotoPaths);

                if (zipPath != null) {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                    String subject = "🚨 SECURITY ALERT: Device Accessed - " + timestamp;

                    Log.d(TAG, ">>> Sending ONE encrypted ZIP via email");
                    GMailSender.sendEmailWithZip(zipPath, subject);


                    for (String photoPath : allPhotoPaths) {
                        try {
                            new File(photoPath).delete();
                            Log.d(TAG, "Deleted individual photo: " + photoPath);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to delete photo: " + photoPath);
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to create ZIP, sending individual photos as fallback");

                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                    String subject = "⚠️ ALERT: Screen Activated - " + timestamp;

                    for (int i = 0; i < allPhotoPaths.size(); i++) {
                        String path = allPhotoPaths.get(i);
                        Log.d(TAG, ">>> Sending photo " + (i + 1) + " as fallback");
                        GMailSender.sendEmailWithAttachment(path, subject + " [Photo #" + (i + 1) + "]");
                    }
                }
            } else {
                Log.w(TAG, "No photos to send!");
            }


            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) {
                    if (error != null) {
                        callback.onCaptureFailed(error);
                    } else {
                        String backPath = backPhotoPaths.isEmpty() ? null : backPhotoPaths.get(0);
                        String frontPath = frontPhotoPaths.isEmpty() ? null : frontPhotoPaths.get(0);
                        callback.onCaptureComplete(backPath, frontPath);
                    }
                }
            });

            Log.d(TAG, "=== finishCapture END ===");
        }).start();
    }
}
