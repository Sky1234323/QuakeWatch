package com.akash.quakewatch.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.databinding.FragmentMapBinding;
import com.akash.quakewatch.ui.detail.QuakeDetailActivity;
import com.akash.quakewatch.util.MagnitudeUtils;
import com.akash.quakewatch.util.TimeUtils;
import com.akash.quakewatch.viewmodel.QuakeViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.events.MapEventsReceiver;

import java.util.List;
import java.util.Locale;

/**
 * MapFragment renders earthquake epicenters on an interactive OpenStreetMap surface
 * using CARTO Voyager tiles, magnitude-colored custom markers, locate-me GPS integration,
 * and a bottom slide-up preview card for inspection and detail navigation.
 */
public class MapFragment extends Fragment {

    private FragmentMapBinding mBinding;
    private QuakeViewModel mViewModel;
    private FusedLocationProviderClient mLocationClient;
    private FolderOverlay mMarkersOverlay;
    private Marker mUserLocationMarker;
    private Quake mSelectedQuake = null;

    private final ActivityResultLauncher<String[]> mLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarseGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                    locateUser(true);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize osmdroid configuration before map view is created
        Configuration.getInstance().setUserAgentValue("QuakeWatch/1.0 (Android student project)");
        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMapBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(requireActivity()).get(QuakeViewModel.class);
        mLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupMap();
        setupControls();
        observeViewModel();
        locateUser(false);
    }

    private void setupMap() {
        mBinding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        mBinding.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mBinding.mapView.setMultiTouchControls(true);

        // Default viewport: South Asia
        GeoPoint defaultCenter = new GeoPoint(20.5937, 78.9629);
        mBinding.mapView.getController().setZoom(4.8);
        mBinding.mapView.getController().setCenter(defaultCenter);

        // Map events receiver to dismiss bottom card when tapping on empty map area
        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                hideBottomPreview();
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        mBinding.mapView.getOverlays().add(mapEventsOverlay);

        mMarkersOverlay = new FolderOverlay();
        mBinding.mapView.getOverlays().add(mMarkersOverlay);
    }

    private void setupControls() {
        mBinding.fabLocateMe.setOnClickListener(v -> checkLocationPermissionAndLocate());

        mBinding.btnClosePreview.setOnClickListener(v -> hideBottomPreview());

        mBinding.btnPreviewViewDetails.setOnClickListener(v -> {
            if (mSelectedQuake != null) {
                Intent intent = new Intent(requireContext(), QuakeDetailActivity.class);
                intent.putExtra(QuakeDetailActivity.EXTRA_QUAKE_ID, mSelectedQuake.getId());
                startActivity(intent);
            }
        });
    }

    private void observeViewModel() {
        mViewModel.getAllQuakes().observe(getViewLifecycleOwner(), quakes -> {
            if (quakes != null) {
                renderQuakeMarkers(quakes);
                mBinding.tvMapSubtitle.setText(String.format(Locale.US, "%d active epicenters", quakes.size()));
            }
        });

        mViewModel.getUserLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                updateUserLocationMarker(location);
            }
        });
    }

    private void renderQuakeMarkers(List<Quake> quakes) {
        mMarkersOverlay.getItems().clear();

        for (Quake quake : quakes) {
            Marker marker = new Marker(mBinding.mapView);
            marker.setPosition(new GeoPoint(quake.getLatitude(), quake.getLongitude()));
            marker.setIcon(createMarkerDrawable(quake.getMagnitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTitle(quake.getPlace());

            marker.setOnMarkerClickListener((m, mapView) -> {
                showBottomPreview(quake);
                mapView.getController().animateTo(m.getPosition());
                return true;
            });

            mMarkersOverlay.add(marker);
        }

        mBinding.mapView.invalidate();
    }

    private void showBottomPreview(Quake quake) {
        mSelectedQuake = quake;

        mBinding.tvPreviewMagnitude.setText(MagnitudeUtils.formatMagnitude(quake.getMagnitude()));
        int colorRes = MagnitudeUtils.severity(quake.getMagnitude()).getColorRes();
        mBinding.tvPreviewMagnitude.getBackground().setTint(ContextCompat.getColor(requireContext(), colorRes));

        mBinding.tvPreviewPlace.setText(quake.getPlace());

        // Extract and display English country/region identifier to clarify non-English map tiles
        String place = quake.getPlace();
        String country = place.contains(",") ? place.substring(place.lastIndexOf(',') + 1).trim() : place;
        mBinding.tvPreviewCountry.setText(String.format(Locale.US, "English Region: %s", country));

        String timeAgo = TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis());
        mBinding.tvPreviewMeta.setText(String.format(Locale.US, "%s \u2022 depth %.0f km", timeAgo, quake.getDepthKm()));

        mBinding.cardBottomPreview.setVisibility(View.VISIBLE);
    }

    private void hideBottomPreview() {
        mSelectedQuake = null;
        mBinding.cardBottomPreview.setVisibility(View.GONE);
    }

    private void checkLocationPermissionAndLocate() {
        boolean hasFine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            locateUser(true);
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Location Permission")
                    .setMessage("QuakeWatch uses your location to center the map and calculate distances to nearby earthquake epicenters.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        mLocationPermissionLauncher.launch(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        });
                    })
                    .setNegativeButton("Not now", null)
                    .show();
        } else {
            mLocationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void locateUser(boolean animateToUser) {
        boolean hasFine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            return;
        }

        try {
            mLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null && isAdded()) {
                    mViewModel.setUserLocation(location);
                    updateUserLocationMarker(location);

                    if (animateToUser) {
                        GeoPoint userPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                        mBinding.mapView.getController().animateTo(userPoint);
                        mBinding.mapView.getController().setZoom(6.5);
                    }
                }
            });
        } catch (SecurityException ignored) {
        }
    }

    private void updateUserLocationMarker(Location location) {
        if (location == null || mBinding == null) return;

        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (mUserLocationMarker == null) {
            mUserLocationMarker = new Marker(mBinding.mapView);
            mUserLocationMarker.setIcon(createUserLocationDrawable());
            mUserLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mUserLocationMarker.setTitle("You are here");
            mBinding.mapView.getOverlays().add(mUserLocationMarker);
        }
        mUserLocationMarker.setPosition(point);
        mBinding.mapView.invalidate();
    }

    private Drawable createMarkerDrawable(double magnitude) {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (34 * density);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int colorRes = MagnitudeUtils.severity(magnitude).getColorRes();
        int color = ContextCompat.getColor(requireContext(), colorRes);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // White border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint);

        // Severity colored circle
        paint.setColor(color);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - (2.5f * density), paint);

        // Magnitude text
        paint.setColor(Color.WHITE);
        paint.setTextSize(11f * density);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        Rect bounds = new Rect();
        String text = MagnitudeUtils.formatMagnitude(magnitude);
        paint.getTextBounds(text, 0, text.length(), bounds);
        float y = (sizePx / 2f) + (bounds.height() / 2f);

        canvas.drawText(text, sizePx / 2f, y, paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createUserLocationDrawable() {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (22 * density);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Outer white ring
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint);

        // Inner vibrant blue dot
        paint.setColor(Color.parseColor("#1A73E8"));
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - (3f * density), paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBinding != null) {
            mBinding.mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBinding != null) {
            mBinding.mapView.onPause();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mBinding != null) {
            mBinding.mapView.onDetach();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
