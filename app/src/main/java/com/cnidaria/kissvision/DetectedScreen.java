package com.cnidaria.kissvision;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetectedScreen extends AppCompatActivity {

    private View loadingWheelView;
    private TextView plateNumberText;
    private TextView checkResultText;
    private FusedLocationProviderClient fusedLocationClient;
    private double lat=0;
    private double lng=0;
    private String city="UNKNOWN";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detected_screen);

        loadingWheelView = findViewById(R.id.loadingWheelView);
        plateNumberText = findViewById(R.id.plateNumberText);
        checkResultText = findViewById(R.id.checkResultText);

        plateNumberText.setVisibility(View.GONE);
        checkResultText.setVisibility(View.GONE);
        loadingWheelView.setVisibility(View.VISIBLE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {

            getCurrentLocation();
        }

        String base64String = getIntent().getStringExtra("croppedBitmap");
        byte[] decodedString = Base64.decode(base64String, Base64.DEFAULT);
        Bitmap croppedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        ImageView imageView = findViewById(R.id.imageView2);
        Button buttonCancel = findViewById(R.id.buttonCancel);
        Button buttonCheck = findViewById(R.id.buttonCheck);
        imageView.setImageBitmap(croppedBitmap);
        sendOCRRequest(base64String);



        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to MainActivity
                Intent intent = new Intent(DetectedScreen.this, MainActivity.class);
                startActivity(intent);
            }
        });

        buttonCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonCheck.setEnabled(false);
                checkResultText.setVisibility(View.GONE);
                loadingWheelView.setVisibility(View.VISIBLE);
                // Send another request
                sendSecondRequest();
                loadingWheelView.setVisibility(View.GONE);
                buttonCheck.setEnabled(true);
                checkResultText.setVisibility(View.VISIBLE);
            }
        });
    }
    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();

                            Geocoder geocoder = new Geocoder(DetectedScreen.this, Locale.getDefault());
                                try {
                                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                                    if (addresses != null && addresses.size() > 0) {
                                        Address address = addresses.get(0);
                                        city=address.getLocality();
                                        System.out.println("GOT USER CITY, IS: "+city);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            Log.d("Location", "Latitude: " + lat + ", Longitude: " + lng);
                        } else {
                            Log.e("Location", "Unable to get current location");
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Log.e("Location", "Location permission denied");
            }
        }
    }
    private void sendOCRRequest(final String base64String) {
        // Send OCR request after 1 second delay
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {

                    Button buttonCheck = findViewById(R.id.buttonCheck);
                    buttonCheck.setEnabled(false);
                    RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());


                    String url = "https://api-inference.huggingface.co/models/microsoft/trocr-large-str";
                    String authToken = "Bearer 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";


                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("image", base64String);
                    final String requestBody = jsonBody.toString();

                    JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.POST, url, null,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    // Handle the response
                                    String plateText = "NONE";
                                    try {
                                        JSONObject obj = (JSONObject) response.get(0);
                                        plateText = obj.getString("generated_text");

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    plateNumberText.setText(plateText);
                                    plateNumberText.setVisibility(View.VISIBLE);

                                    loadingWheelView.setVisibility(View.GONE);
                                    buttonCheck.setEnabled(true);
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // Handle errors
                                    Log.e("Volley Error", error.toString());
                                    plateNumberText.setText("NONE");
                                    plateNumberText.setVisibility(View.VISIBLE);
                                    // Hide loading wheel
                                    loadingWheelView.setVisibility(View.GONE);
                                    buttonCheck.setEnabled(true);
                                }
                            }) {
                        @Override
                        public String getBodyContentType() {
                            return "application/json; charset=utf-8";
                        }

                        @Override
                        public byte[] getBody() {
                            try {
                                return requestBody == null ? null : requestBody.getBytes("utf-8");
                            } catch (UnsupportedEncodingException uee) {
                                return null;
                            }
                        }

                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            Map<String, String> headers = new HashMap<>();
                            headers.put("Authorization", authToken);
                            return headers;
                        }
                    };

                    requestQueue.add(jsonArrayRequest);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, 1000); // 1 second delay
    }

    private void sendSecondRequest() {
        String licensePlate = plateNumberText.getText().toString();
        System.out.println("SENDING PLATE VERIFICATION REQUEST WITH LICENSE PLATE: ("+licensePlate+")");
        String backendUrl = "http://192.168.100.234:5000/car-info/" + licensePlate;
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET,
                backendUrl,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            SharedPreferences sharedPreferences = getSharedPreferences("JournalData", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.apply();
                            String status= response.getString("response");
                            System.out.println("GOT RESPONSE FORM BACKEND: "+response.toString());
                            if(status.equals("STOLEN")){
                                checkResultText.setText("This car is STOLEN! Go to journal!");
                                checkResultText.setTextColor(Color.RED);
                                checkResultText.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Intent intent = new Intent(DetectedScreen.this, JournalActivity.class);
                                        startActivity(intent);
                                    }
                                });
                            }else{
                                checkResultText.setText("This car is NOT STOLEN!");
                                checkResultText.setTextColor(Color.GREEN);
                                checkResultText.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                    }
                                });
                            }
                            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                            response.put("city",city);
                            editor.putString(timestamp, response.toString());
                            editor.apply();
                            System.out.println("SUCCESFULLY PUT STRING!");
                        } catch (JSONException e) {
                            System.out.println("ERROR!!!!!!!!!!!!");
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle errors
                        Log.e("Volley Error", error.toString());
                    }
                }
        );

        // Add the request to the request queue
        requestQueue.add(jsonObjectRequest);
    }
}
