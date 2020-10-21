package com.example.cavoid.workers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.Response;
import com.example.cavoid.activities.LoadingActivity;
import com.example.cavoid.api.Repository;
import com.example.cavoid.database.LocationDao;
import com.example.cavoid.database.LocationDatabase;
import com.example.cavoid.database.PastLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class RegularLocationSaveWorker extends Worker {
    public RegularLocationSaveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String TAG = RegularLocationSaveWorker.class.getName();

        LocationDatabase locDb = LocationDatabase.getDatabase(getApplicationContext());
        LocationDao dao = locDb.getLocationDao();
        Repository repo = new Repository(getApplicationContext());

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
            return Result.failure();
        }
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setNumUpdates(2);
        locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location == null) {
                    Log.w(TAG, "Could not find user's location!");
                    return;
                }

                Log.i(TAG, "Saving location: " + location.toString());
                LocalDate date = LocalDate.now();
                try {
                    repo.getFipsCodeFromCurrentLocation(location, new Response.Listener<JSONObject>(){

                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                PastLocation pastLocation = new PastLocation();
                                pastLocation.fips = response.getJSONArray("results").getJSONObject(0).getString("county_fips");
                                pastLocation.countyName = response.getJSONArray("results").getJSONObject(0).getString("county_name");
                                pastLocation.date = date;
                                pastLocation.wasNotified = false;
                                LocationDatabase.databaseWriteExecutor.execute(() -> dao.insertLocations(pastLocation));
                                Log.i(TAG, "Saved location: " + pastLocation.fips);
                            } catch (JSONException e) {
                                Log.w(TAG, "Could not fetch current fips...\n" + e.toString());

                            }

                        }
                    });
                } catch (IOException e) {
                    Log.w(TAG, e.toString());
                }

            }

            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        return Result.success();
    }
}
