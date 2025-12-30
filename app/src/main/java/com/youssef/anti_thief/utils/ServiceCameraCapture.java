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
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;


public class ServiceCameraCapture {

    private static final String TAG = "ServiceCameraCapture";

    private final Context context;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private CameraCaptureSession captureSession;
    
    private String backPhotoPath;
    private String frontPhotoPath;
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
        this.backPhotoPath = null;
        this.frontPhotoPath = null;

        Log.d(TAG, "=== STARTING SERVICE-BASED CAMERA CAPTURE ===");
        Log.d(TAG, "No Activity needed - capturing directly from service");
        
        startBackgroundThread();
        

        captureFromCamera(false);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(1000); // Wait max 1 second
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            } finally {
                backgroundThread = null;
                backgroundHandler = null;
            }
        }
    }

    private void captureFromCamera(boolean isFront) {
        String cameraName = isFront ? "FRONT" : "BACK";
        Log.d(TAG, "Capturing from " + cameraName + " camera");

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

                    backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
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
                        backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
                    } else {
                        finishCapture(null);
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, cameraName + " camera error: " + error);
                    camera.close();
                    if (!isFront) {
                        backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
                    } else {
                        finishCapture(null);
                    }
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
            if (!isFront) {
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
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

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        
                        String path = saveImage(bytes, isFront);
                        if (isFront) {
                            frontPhotoPath = path;
                        } else {
                            backPhotoPath = path;
                        }
                        Log.d(TAG, (isFront ? "Front" : "Back") + " photo saved: " + path);
                    }
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
                

                closeCamera();
                

                if (!isFront) {
                    backgroundHandler.postDelayed(() -> captureFromCamera(true), 300);
                } else {
                    finishCapture(null);
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
                            

                            backgroundHandler.postDelayed(() -> takePicture(isFront), 500);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                            closeCamera();
                            if (!isFront) {
                                backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
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
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
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

            Log.d(TAG, "Taking picture: " + (isFront ? "FRONT" : "BACK"));

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
                    Log.d(TAG, "Capture completed: " + (isFront ? "FRONT" : "BACK"));
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error taking picture", e);
            closeCamera();
            if (!isFront) {
                backgroundHandler.postDelayed(() -> captureFromCamera(true), 500);
            } else {
                finishCapture(e.getMessage());
            }
        }
    }

    private String saveImage(byte[] bytes, boolean isFront) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = (isFront ? "front_" : "back_") + timestamp + ".jpg";
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
        Log.d(TAG, "Back photo: " + backPhotoPath);
        Log.d(TAG, "Front photo: " + frontPhotoPath);


        final String backPath = backPhotoPath;
        final String frontPath = frontPhotoPath;

        closeCamera();
        isCapturing = false;


        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }


        new Handler(Looper.getMainLooper()).post(() -> {
            if (backPath != null || frontPath != null) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                String subject = "⚠️ ALERT: Screen Activated - " + timestamp;

                if (backPath != null) {
                    Log.d(TAG, ">>> Calling GMailSender for BACK photo");
                    GMailSender.sendEmailWithAttachment(backPath, subject + " [BACK]");
                }
                if (frontPath != null) {
                    Log.d(TAG, ">>> Calling GMailSender for FRONT photo");
                    GMailSender.sendEmailWithAttachment(frontPath, subject + " [FRONT]");
                }
            } else {
                Log.w(TAG, "No photos to send!");
            }

            if (callback != null) {
                if (error != null) {
                    callback.onCaptureFailed(error);
                } else {
                    callback.onCaptureComplete(backPath, frontPath);
                }
            }
            Log.d(TAG, "=== finishCapture END ===");
        });
    }
}
