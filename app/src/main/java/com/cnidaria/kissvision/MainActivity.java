package com.cnidaria.kissvision;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private int screenWidth=0;
    private int screenHeight=0;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ExecutorService executorService;
    private SurfaceHolder surfaceHolder;
    private void initializeCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // Use the first available camera

            // Open the camera
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    // Camera opened successfully, proceed with setting up preview
                    try {
                        setupCameraPreview(camera);
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    // Handle camera disconnect
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    // Handle camera error
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void processImage(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            Log.e("Quality","Image found!");
        }else{
            Log.e("Quality","Image found!");
        }
    }
    private void setScreenWidthHeight(){
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
    }
    private void setupCameraPreview(CameraDevice cameraDevice) throws CameraAccessException {
        // Create a SurfaceView for camera preview
        SurfaceView surfaceView = findViewById(R.id.surfaceView);

        // Get the SurfaceHolder and set it as a target for the camera preview
        surfaceHolder = surfaceView.getHolder();

        surfaceHolder.setFixedSize(screenWidth, screenHeight); // Set your preferred preview size

        // Create a camera capture session
        cameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                // Configure the camera capture session
                try {
                    // Create a capture request and set it to repeating for continuous preview
                    CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    captureRequestBuilder.addTarget(surfaceHolder.getSurface());

                    session.setRepeatingRequest(captureRequestBuilder.build(), null, null);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                // Handle configuration failure
            }
        }, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setScreenWidthHeight();
        backgroundThread = new HandlerThread("BackgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        executorService = Executors.newSingleThreadExecutor();
        initializeCamera();
        ImageReader imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(this::processImage, backgroundHandler);
    }

}