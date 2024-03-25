package com.cnidaria.kissvision;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class JournalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);

        // Get SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("JournalData", Context.MODE_PRIVATE);

        // Get a reference to the root layout
        LinearLayout rootLayout = findViewById(R.id.root);

        // Get all keys (timestamps) from SharedPreferences
        Map<String, ?> allEntries = sharedPreferences.getAll();
        System.out.println("Journal creating....");
        // Iterate through each entry
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            // Get JSON string from SharedPreferences
            String jsonString = entry.getValue().toString();
            System.out.println("HERE");
            try {
                // Parse JSON object
                JSONObject jsonObject = new JSONObject(jsonString);

                System.out.println(jsonObject);
                String response = jsonObject.getString("response");

                // Create a layout based on the response
                View layout;
                if (response.equals("CLEAN")) {
                    System.out.println("CLEAR RESPONSE");
                    layout = getLayoutInflater().inflate(R.layout.clear_layout,null);
                    TextView plate=layout.findViewById(R.id.textViewClearPlate);
                    plate.setText("Plate Number : "+ jsonObject.getString("license_plate"));
                    TextView timestamp=layout.findViewById(R.id.textViewClearTimestamp);
                    timestamp.setText("Timestamp : "+entry.getKey()+" at "+jsonObject.getString("city"));

                } else if (response.equals("STOLEN")) {
                    System.out.println("STOLEN RESPONSE");
                    layout = getLayoutInflater().inflate(R.layout.stolen_layout,null);
                    TextView plate= layout.findViewById(R.id.plateNumberText);
                    TextView timestamp=layout.findViewById(R.id.timestampText);
                    TextView make=layout.findViewById(R.id.carMakeText);
                    TextView stolen_city=layout.findViewById(R.id.cityStolenText);
                    TextView steal_date=layout.findViewById(R.id.stealDateText);
                    plate.setText("Plate Number : "+ jsonObject.getString("license_plate"));
                    timestamp.setText("Timestamp : "+entry.getKey()+" at "+jsonObject.getString("city"));
                    make.setText(jsonObject.getString("car_model"));
                    stolen_city.setText(jsonObject.getString("unitate"));
                    steal_date.setText(jsonObject.getString("data_furtului"));

                } else {
                    System.out.println("NEITHER");
                    continue;
                }

                // Add the constructed layout to the root layout
                rootLayout.addView(layout);
                System.out.println("GREAT SUCCESS!");

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }



}
