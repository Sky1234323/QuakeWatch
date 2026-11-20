package com.akash.practical8_new;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.practical8_new.adapter.SyncLogAdapter;
import com.akash.practical8_new.databinding.ActivityMainBinding;
import com.akash.practical8_new.firebase.FirebaseSyncHelper;
import com.akash.practical8_new.model.LocationModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Practical No. 08: Real-time Location Service and Maps with Firebase Data Sync.
 * Implements Google Maps, FusedLocationProviderClient, and bidirectional Firebase Firestore sync.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private static final String TAG = "Practical8_Main";

    private ActivityMainBinding binding;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseSyncHelper firebaseSyncHelper;

    private Marker currentLocationMarker;
    private final List<Marker> cloudSyncedMarkers = new ArrayList<>();
    private final List<LocationModel> liveSyncList = new ArrayList<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private boolean isTrackingEnabled = false;
    private double currentLat = 16.8524; // Default Sangli/Maharashtra region
    private double currentLng = 74.5815;
    private float currentAccuracy = 5.0f;
    private int simulationStep = 1;

    // Runtime Permission Launcher
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                    enableMyLocationOnMap();
                    fetchCurrentLocation();
                } else {
                    Toast.makeText(this, "Permission denied. Using fallback coordinates.", Toast.LENGTH_LONG).show();
                    updateLocationDisplay(currentLat, currentLng, 15.0f, "Location permission not granted");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firebaseSyncHelper = new FirebaseSyncHelper(this);

        initMap();
        initControls();
        initLocationCallback();
        startFirebaseRealtimeSync();

        checkAndRequestPermissions();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.setOnMapClickListener(this);

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        LatLng initialPos = new LatLng(currentLat, currentLng);
        currentLocationMarker = googleMap.addMarker(new MarkerOptions()
                .position(initialPos)
                .title("Current Location")
                .snippet("Ready to sync")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 14f));

        if (hasLocationPermission()) {
            enableMyLocationOnMap();
        }
    }

    @Override
    public void onMapClick(@NonNull LatLng latLng) {
        // Tapping anywhere on the map creates a custom pin and syncs it to Firebase
        updateLocationDisplay(latLng.latitude, latLng.longitude, 1.0f, "Pinned on Map");
        geocodeAndSync(latLng.latitude, latLng.longitude, 1.0f, "Custom Pinned Location");
        Toast.makeText(this, "Location pinned & synced to Firebase!", Toast.LENGTH_SHORT).show();
    }

    private void initControls() {
        // 1. Get Current Location Button
        binding.btnGetLocation.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                fetchCurrentLocation();
            } else {
                checkAndRequestPermissions();
            }
        });

        // 2. Real-time Tracking Toggle
        binding.switchRealtimeTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isTrackingEnabled = isChecked;
            if (isChecked) {
                if (hasLocationPermission()) {
                    startLocationUpdates();
                    Toast.makeText(this, "Real-time location tracking started", Toast.LENGTH_SHORT).show();
                } else {
                    binding.switchRealtimeTracking.setChecked(false);
                    checkAndRequestPermissions();
                }
            } else {
                stopLocationUpdates();
                Toast.makeText(this, "Real-time tracking stopped", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. Simulate Movement / Add Pin Button (Extremely useful for viva presentation)
        binding.btnSimulatePin.setOnClickListener(v -> simulateMovementStep());

        // 4. Map Type Switcher (Normal / Satellite)
        binding.btnMapType.setOnClickListener(v -> {
            if (googleMap != null) {
                if (googleMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    binding.btnMapType.setText("Normal Map");
                } else {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    binding.btnMapType.setText("Satellite");
                }
            }
        });

        // 5. Recenter FAB
        binding.fabMyLocation.setOnClickListener(v -> {
            if (googleMap != null) {
                LatLng pos = new LatLng(currentLat, currentLng);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
            }
        });

        // 6. View Firebase Sync Logs Dialog
        binding.btnViewLogs.setOnClickListener(v -> showSyncLogsDialog());
    }

    private void initLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLng = location.getLongitude();
                    currentAccuracy = location.getAccuracy();
                    geocodeAndSync(currentLat, currentLng, currentAccuracy, "GPS Live Stream");
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void fetchCurrentLocation() {
        if (!hasLocationPermission()) return;

        binding.tvAccuracy.setText("GPS: Locating...");
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        currentAccuracy = location.getAccuracy();
                        geocodeAndSync(currentLat, currentLng, currentAccuracy, "Current GPS Fix");
                        Toast.makeText(this, "GPS Fix obtained!", Toast.LENGTH_SHORT).show();
                    } else {
                        // Fallback to getLastLocation if GPS warm-up takes time
                        fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                currentLat = lastLoc.getLatitude();
                                currentLng = lastLoc.getLongitude();
                                currentAccuracy = lastLoc.getAccuracy();
                                geocodeAndSync(currentLat, currentLng, currentAccuracy, "Last Known Fix");
                            } else {
                                geocodeAndSync(currentLat, currentLng, 10.0f, "Default Location Fix");
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location error: " + e.getMessage());
                    geocodeAndSync(currentLat, currentLng, 15.0f, "Simulated Fix");
                });
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setMinUpdateDistanceMeters(1.0f)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    /**
     * Resolves human-readable address on background thread and syncs to Firebase Firestore.
     */
    private void geocodeAndSync(double lat, double lng, float accuracy, String sourceLabel) {
        updateLocationDisplay(lat, lng, accuracy, "Resolving address...");

        executorService.execute(() -> {
            String resolvedAddress = String.format(Locale.US, "Location: %.4f° N, %.4f° E (GPS Position)", lat, lng);
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        StringBuilder sb = new StringBuilder();
                        if (addr.getLocality() != null) sb.append(addr.getLocality()).append(", ");
                        if (addr.getAdminArea() != null) sb.append(addr.getAdminArea()).append(", ");
                        if (addr.getCountryName() != null) sb.append(addr.getCountryName());
                        if (sb.length() > 0) {
                            resolvedAddress = sb.toString();
                        } else if (addr.getAddressLine(0) != null) {
                            resolvedAddress = addr.getAddressLine(0);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocoder notice: " + e.getMessage());
                // Fallback to formatted landmark/region
                resolvedAddress = String.format(Locale.US, "Area around %.4f° N, %.4f° E", lat, lng);
            }

            final String finalAddress = resolvedAddress;
            mainHandler.post(() -> {
                updateLocationDisplay(lat, lng, accuracy, finalAddress);

                // Update Map Marker
                if (googleMap != null) {
                    LatLng pos = new LatLng(lat, lng);
                    if (currentLocationMarker == null) {
                        currentLocationMarker = googleMap.addMarker(new MarkerOptions()
                                .position(pos)
                                .title(sourceLabel)
                                .snippet(finalAddress)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                    } else {
                        currentLocationMarker.setPosition(pos);
                        currentLocationMarker.setTitle(sourceLabel);
                        currentLocationMarker.setSnippet(finalAddress);
                    }
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                }

                // Sync to Firebase Cloud Firestore
                LocationModel model = new LocationModel(
                        "loc_" + System.currentTimeMillis(),
                        lat,
                        lng,
                        accuracy,
                        finalAddress,
                        System.currentTimeMillis(),
                        Build.MANUFACTURER + " " + Build.MODEL
                );

                firebaseSyncHelper.syncLocation(model, new FirebaseSyncHelper.SyncCallback() {
                    @Override
                    public void onSuccess(String documentId) {
                        String timeStr = timeFormat.format(new Date());
                        binding.tvFirebaseStatus.setText("☁️ Firebase: Synced (" + timeStr + ")");
                    }

                    @Override
                    public void onError(Exception e) {
                        binding.tvFirebaseStatus.setText("☁️ Firebase: Offline Cache (Pending sync)");
                    }
                });
            });
        });
    }

    private void updateLocationDisplay(double lat, double lng, float accuracy, String address) {
        binding.tvCoordinates.setText(String.format(Locale.US, "Lat: %.5f° N, Lng: %.5f° E", lat, lng));
        binding.tvAccuracy.setText(String.format(Locale.US, "± %.1fm", accuracy));
        binding.tvAddress.setText(address);
    }

    /**
     * Simulates movement steps for convenient demonstration during college viva/presentation.
     */
    private void simulateMovementStep() {
        // Move by small delta ~150 meters
        double delta = 0.0015 * simulationStep;
        currentLat += (simulationStep % 2 == 0 ? delta : -delta * 0.7);
        currentLng += (simulationStep % 2 == 0 ? delta * 0.5 : delta);
        simulationStep++;

        geocodeAndSync(currentLat, currentLng, 3.5f, "Simulated Step #" + (simulationStep - 1));
        Toast.makeText(this, "Movement simulated & synced to Firebase!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Starts listening for live real-time Firestore database synchronization.
     */
    private void startFirebaseRealtimeSync() {
        firebaseSyncHelper.startRealtimeListener(new FirebaseSyncHelper.RealtimeUpdateCallback() {
            @Override
            public void onDataUpdated(List<LocationModel> locations) {
                liveSyncList.clear();
                liveSyncList.addAll(locations);
                binding.tvSyncCount.setText("Live Sync: " + liveSyncList.size() + " records");
                binding.tvFirebaseStatus.setText("☁️ Firebase: Live Synced (" + timeFormat.format(new Date()) + ")");

                // Plot synced markers on Google Maps
                plotCloudMarkers(locations);
            }

            @Override
            public void onError(Exception e) {
                binding.tvFirebaseStatus.setText("☁️ Firebase: Sync Error (" + e.getMessage() + ")");
            }
        });
    }

    private void plotCloudMarkers(List<LocationModel> locations) {
        if (googleMap == null) return;

        // Remove previous cloud markers
        for (Marker m : cloudSyncedMarkers) {
            m.remove();
        }
        cloudSyncedMarkers.clear();

        // Add latest up to 5 markers
        int count = Math.min(locations.size(), 5);
        for (int i = 0; i < count; i++) {
            LocationModel item = locations.get(i);
            LatLng pos = new LatLng(item.getLatitude(), item.getLongitude());
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Cloud Sync #" + (i + 1) + " (" + item.getDeviceName() + ")")
                    .snippet(item.getAddress())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            if (marker != null) {
                cloudSyncedMarkers.add(marker);
            }
        }
    }

    private void showSyncLogsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sync_logs, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_sync_logs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        SyncLogAdapter adapter = new SyncLogAdapter(new ArrayList<>(liveSyncList));
        recyclerView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationOnMap() {
        if (googleMap != null && hasLocationPermission()) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestPermissions() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        if (firebaseSyncHelper != null) {
            firebaseSyncHelper.stopRealtimeListener();
        }
        executorService.shutdown();
    }
}
