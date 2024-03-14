package com.cnidaria.kissvision;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import android.content.Context;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;

import android.hardware.camera2.CameraManager;

import android.media.Image;
import android.media.ImageReader;

import android.os.Bundle;

import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private ModelRunner modelRunner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("Quality","Camera app starting!");

        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CAMERA}, 121);
        } else {
            //TODO show live camera footage
            setFragment();
        }
        try {
            modelRunner = new ModelRunner(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //TODO show live camera footage
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            setFragment();
        } else {

        }
    }
    //TODO fragment which show llive footage from camera
    int previewHeight = 0,previewWidth = 0;
    int sensorOrientation;
    protected void setFragment() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CameraConnectionFragment fragment;
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                Log.d("tryOrientation","rotation: "+rotation+"   orientation: "+getScreenOrientation()+"  "+previewWidth+"   "+previewHeight);
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,
                        R.layout.camera_fragment,
                        new Size(640, 480));

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;
    @Override
    public void onImageAvailable(ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();

        } catch (final Exception e) {
            Log.d("tryError",e.getMessage());
            return;
        }

    }
    private void drawBoundingBox(final float[] results) {
        // Post a Runnable to the main thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Extract bounding box coordinates from results array
                float xmin = results[0];
                float ymin = results[1];
                float xmax = results[2];
                float ymax = results[3];

                // Get the device's screen width and height
                WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                DisplayMetrics displayMetrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getMetrics(displayMetrics);
                int screenWidth = displayMetrics.widthPixels;
                int screenHeight = displayMetrics.heightPixels;

                // Scale the bounding box coordinates to match the original screen size
                float scaleX = (float) screenWidth / 128f;
                float scaleY = (float) screenHeight / 128f;
                float scaledXmin = xmin * scaleX;
                float scaledYmin = ymin * scaleY;
                float scaledXmax = xmax * scaleX;
                float scaledYmax = ymax * scaleY;

                // Draw the square on the ImageView
                ImageView imageView = findViewById(R.id.imageView);
                Drawable drawable = imageView.getDrawable();
                if (drawable == null) {
                    // Set a transparent background color
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                }
                try{
                Bitmap bitmap = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5);
                canvas.drawRect(scaledXmin, scaledYmin, scaledXmax, scaledYmax, paint);
                System.out.println("Success!");
                imageView.setImageBitmap(bitmap);}catch (Exception e){
                    System.out.println("Fail!");
                    Log.e("BoundingBoxDraw","Exception: "+e.getMessage());
                }
            }
        });
    }
    private void processImage() {
        imageConverter.run();
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        //Do your work here
        Log.w("Quality!","Aight G!");
        float[] results=modelRunner.runInference(bitmapToFloatArray(rgbFrameBitmap));
        drawBoundingBox(results);
        Log.w("Great success!", Arrays.toString(results));
        postInferenceCallback.run();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
    private float[][][][] bitmapToFloatArray(Bitmap bitmap) {
        if (bitmap == null) {
            return new float[0][0][0][0]; // Return an empty tensor if the input bitmap is null
        }

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 128, 128, true);

        if (scaledBitmap == null) {
            return new float[0][0][0][0]; // Return an empty tensor if the scaled bitmap is null
        }

        float[][][][] floatValues = new float[1][128][128][3];

        for (int y = 0; y < scaledBitmap.getHeight(); y++) {
            for (int x = 0; x < scaledBitmap.getWidth(); x++) {
                int color = scaledBitmap.getPixel(x, y);
                float red = ((color >> 16) & 0xFF) / 255.0f;
                float green = ((color >> 8) & 0xFF) / 255.0f;
                float blue = (color & 0xFF) / 255.0f;
                floatValues[0][y][x][0] = red;
                floatValues[0][y][x][1] = green;
                floatValues[0][y][x][2] = blue;
            }
        }

        return floatValues;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the TensorFlow Lite model runner
        if (modelRunner != null) {
            modelRunner.close();
        }
    }

}