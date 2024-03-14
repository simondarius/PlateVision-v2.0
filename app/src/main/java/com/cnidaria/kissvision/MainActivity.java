package com.cnidaria.kissvision;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import android.content.Context;
import android.content.pm.PackageManager;

import android.hardware.camera2.CameraAccessException;

import android.hardware.camera2.CameraManager;

import android.media.Image;
import android.media.ImageReader;

import android.os.Bundle;

import android.util.Log;
import android.util.Size;
import android.view.Surface;


public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;








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
    @Override
    public void onImageAvailable(ImageReader reader) {
        final Image image = reader.acquireLatestImage();
        Log.w("Quality","GOT AN IMAGE!");
        image.close();
    }

}