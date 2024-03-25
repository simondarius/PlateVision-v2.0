package com.cnidaria.kissvision;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;

import android.hardware.camera2.CameraManager;

import android.media.Image;
import android.media.ImageReader;

import android.os.Bundle;

import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private static final int PERMISSION_REQUEST_INTERNET = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("Quality", "Camera app starting!");
        ImageButton buttonImg = findViewById(R.id.journalButton);

        buttonImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("Going to journal");
                Intent intent = new Intent(MainActivity.this,JournalActivity.class);
                startActivity(intent);
            }
        });
        // Retrieve display metrics
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Extract width and height
        int screenWidth = displayMetrics.widthPixels;

        // Calculate height for 2:3 aspect ratio
        int screenHeight = (int) (screenWidth * (3.0f / 2.0f));

        // Log width and height
        Log.e("Screen Info", "Width: " + screenWidth + "px");
        Log.e("Screen Info", "Height: " + screenHeight + "px");

        // Find the FrameLayout in your layout
        FrameLayout frameLayout = findViewById(R.id.container);

        ViewGroup.LayoutParams frameLayoutParams = frameLayout.getLayoutParams();
        frameLayoutParams.height = screenHeight;

        frameLayout.setLayoutParams(frameLayoutParams);


        ImageView imageView = findViewById(R.id.imageView);


        ViewGroup.LayoutParams imageLayoutParams = imageView.getLayoutParams();
        imageLayoutParams.width = frameLayoutParams.width;
        imageLayoutParams.height = frameLayoutParams.height;
        imageView.setLayoutParams(imageLayoutParams);

        // Set the position of the ImageView to overlap with the FrameLayout
        imageView.setX(frameLayout.getX());
        imageView.setY(frameLayout.getY());
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) {
            imageView.setBackgroundColor(Color.TRANSPARENT);
        }

        // Request INTERNET permission if not granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.INTERNET}, PERMISSION_REQUEST_INTERNET);
        }

        // Request CAMERA permission if not granted
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 121);
        } else {
            //TODO show live camera footage
            setFragment();
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
    private void drawBoundingBox(final JSONArray predictionsArray) {

        runOnUiThread(new Runnable() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void run() {
                try {
                    ImageView imageView = findViewById(R.id.imageView);


                    imageView.setImageBitmap(null);
                    imageView.invalidate();



                    int originalImageWidth = 480; // Width of the original image
                    int originalImageHeight = 640; // Height of the original image

                    float scaleX = (float) imageView.getWidth() / originalImageWidth;
                    float scaleY = (float) imageView.getHeight() / originalImageHeight;

                    Bitmap bitmap = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    Paint strokePaint = new Paint();
                    strokePaint.setColor(Color.RED);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(5); // Thicker stroke for the bounding box

                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(20); // Text size for class label and confidence score

                    for (int i = 0; i < predictionsArray.length(); i++) {
                        JSONObject prediction = predictionsArray.getJSONObject(i);

                        float x1 = (float) prediction.getDouble("x1");
                        float y1 = (float) prediction.getDouble("y1");
                        float x2 = (float) prediction.getDouble("x2");
                        float y2 = (float) prediction.getDouble("y2");

                        float scaledX1 = (x1 * scaleX) + imageView.getWidth() * 0.065f;
                        float scaledY1 = y1 * scaleY;
                        float scaledX2 = (x2 * scaleX) + imageView.getWidth() * 0.065f;
                        float scaledY2 = y2 * scaleY;

                        // Draw the rectangle
                        canvas.drawRect(scaledX1, scaledY1, scaledX2, scaledY2, strokePaint);
                        float textX = scaledX1 + 10;
                        float textY = scaledY1 - 15;
                        RectF backgroundRect = new RectF(textX - 5, textY - 10 - 10, textX + 300 + 5, textY + 5);
                        Paint backgroundPaint = new Paint();
                        backgroundPaint.setColor(Color.RED);
                        canvas.drawRect(backgroundRect, backgroundPaint);
                        // Draw the class label and confidence score
                        String classLabel = "Plate";
                        double confidenceScore = prediction.getDouble("score");
                        String confidenceText = String.format("Confidence: %.2f", confidenceScore);
                        String labelText = classLabel + " - " + confidenceText;

                        textPaint.setStrokeWidth(3);

                        canvas.drawText(labelText, textX - 1, textY, textPaint);
                        canvas.drawText(labelText, textX + 1, textY, textPaint);
                        canvas.drawText(labelText, textX, textY - 1, textPaint);
                        canvas.drawText(labelText, textX, textY + 1, textPaint);


                        canvas.drawText(labelText, textX, textY, textPaint);
                    }

                    imageView.setImageBitmap(bitmap);

                    imageView.setOnTouchListener(new View.OnTouchListener() {
                        private boolean touchHandled = false;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (!touchHandled && event.getAction() == MotionEvent.ACTION_DOWN) {
                                float clickX = event.getX();
                                float clickY = event.getY();
                                System.out.println("Click in imageView: " + clickX + " : " + clickY);

                                for (int i = 0; i < predictionsArray.length(); i++) {
                                    try {
                                        JSONObject prediction = predictionsArray.getJSONObject(i);
                                        float x1 = (float) prediction.getDouble("x1") * scaleX + imageView.getX() + imageView.getWidth() * 0.065f;
                                        float y1 = (float) prediction.getDouble("y1") * scaleY + imageView.getY();
                                        float x2 = (float) prediction.getDouble("x2") * scaleX + imageView.getX() + imageView.getWidth() * 0.065f;
                                        float y2 = (float) prediction.getDouble("y2") * scaleY + imageView.getY();
                                        System.out.println("Bounding box coordinates: " + x1 + " : " + y1 + " and " + x2 + " : " + y2);

                                        if (clickX >= x1 && clickX <= x2 && clickY >= y1 && clickY <= y2) {
                                            String croppedBitmap = prediction.getString("cropped_image_base64");

                                            Intent intent = new Intent(MainActivity.this, DetectedScreen.class);
                                            intent.putExtra("croppedBitmap", croppedBitmap);
                                            startActivity(intent);
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            return true;
                        }
                    });

                } catch (JSONException e) {
                    // Handle JSON parsing error
                    Log.e("drawBoundingBox", "JSONException: " + e.getMessage());
                }
            }
        });
    }
    private void processImage() {
        imageConverter.run();
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);


        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        rgbFrameBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT);

        String backendUrl = "http://192.168.100.234:5000/detect";

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("image", base64Image);
            jsonArray.put(jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.POST, backendUrl, jsonArray,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                       drawBoundingBox(response);
                       postInferenceCallback.run();
                    }
                },
                new Response.ErrorListener() {@Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle error
                        Log.e("Backend Error", error.toString());
                        postInferenceCallback.run();
                    }
                });


        Volley.newRequestQueue(this).add(jsonArrayRequest);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

}