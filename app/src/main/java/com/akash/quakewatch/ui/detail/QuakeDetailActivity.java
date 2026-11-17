package com.akash.quakewatch.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.akash.quakewatch.R;
import com.akash.quakewatch.ai.GeminiClient;
import com.akash.quakewatch.data.FeltRepository;
import com.akash.quakewatch.data.QuakeRepository;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.databinding.ActivityQuakeDetailBinding;
import com.akash.quakewatch.util.GeoUtils;
import com.akash.quakewatch.util.MagnitudeUtils;
import com.akash.quakewatch.util.TimeUtils;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

/**
 * QuakeDetailActivity presents comprehensive information about an individual earthquake.
 * It features a 44sp magnitude hero badge, three-column stat breakdown, magnitude scale indicator,
 * non-interactive epicenter mini-map, safety field guide, and external USGS event linking.
 */
public class QuakeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_QUAKE_ID = "extra_quake_id";

    private ActivityQuakeDetailBinding mBinding;
    private Quake mQuake;
    private ListenerRegistration mFeltRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Configure osmdroid before inflating mini map
        Configuration.getInstance().setUserAgentValue("QuakeWatch/1.0 (Android student project)");
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        mBinding = ActivityQuakeDetailBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.detailRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setupToolbar();

        String quakeId = getIntent().getStringExtra(EXTRA_QUAKE_ID);
        if (quakeId != null && !quakeId.isEmpty()) {
            loadQuake(quakeId);
        } else {
            Toast.makeText(this, "Earthquake not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupToolbar() {
        mBinding.btnBack.setOnClickListener(v -> finish());
        mBinding.btnShare.setOnClickListener(v -> shareQuake());
    }

    private void loadQuake(String quakeId) {
        QuakeRepository.getInstance(this).getQuakeById(quakeId, quake -> {
            if (quake == null) {
                Toast.makeText(this, "Unable to load event details", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            mQuake = quake;
            bindQuakeData(quake);
        });
    }

    private void bindQuakeData(Quake quake) {
        // 1. Hero Card
        mBinding.tvHeroMagnitude.setText(MagnitudeUtils.formatMagnitude(quake.getMagnitude()));
        int colorRes = MagnitudeUtils.severity(quake.getMagnitude()).getColorRes();
        int severityColor = ContextCompat.getColor(this, colorRes);
        mBinding.tvHeroMagnitude.getBackground().setTint(severityColor);

        MagnitudeUtils.Severity severity = MagnitudeUtils.severity(quake.getMagnitude());
        mBinding.tvHeroSeverityLabel.setText(severity.getLabel().toUpperCase(Locale.US) + " SHAKING");
        mBinding.tvHeroSeverityLabel.setTextColor(severityColor);

        mBinding.tvHeroPlace.setText(quake.getPlace());

        // Extract and display English country/region identifier to clarify non-English map tiles
        String place = quake.getPlace();
        String country = place.contains(",") ? place.substring(place.lastIndexOf(',') + 1).trim() : place;
        mBinding.tvHeroCountry.setText(String.format(Locale.US, "English Region: %s", country));

        mBinding.tvHeroDatetime.setText(TimeUtils.formatDateTime(quake.getTimeMs()));

        // 2. Three-Column Stat Row
        String timeAgo = TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis());
        mBinding.tvStatTimeago.setText(timeAgo);

        String magWithType = String.format(Locale.US, "%.1f %s", quake.getMagnitude(), quake.getMagType());
        mBinding.tvStatMagType.setText(magWithType.trim());

        mBinding.tvStatDepth.setText(String.format(Locale.US, "%.0f km", quake.getDepthKm()));

        // 3. Magnitude Scale Needle Position
        positionScaleNeedle(quake.getMagnitude());

        // 4. Mini Epicenter Map
        setupMiniMap(quake);

        // 5. Coordinates & User Distance
        Location userLoc = QuakeRepository.getInstance(this).getUserLocation();
        if (userLoc != null) {
            double dist = GeoUtils.distanceKm(userLoc.getLatitude(), userLoc.getLongitude(),
                    quake.getLatitude(), quake.getLongitude());
            mBinding.tvDetailDistance.setText(GeoUtils.formatDistance(dist));
        } else {
            mBinding.tvDetailDistance.setText("Distance from you: Location unavailable");
        }

        String latDir = quake.getLatitude() >= 0 ? "N" : "S";
        String lonDir = quake.getLongitude() >= 0 ? "E" : "W";
        mBinding.tvDetailCoordinates.setText(String.format(Locale.US, "Coordinates: %.4f\u00B0 %s, %.4f\u00B0 %s",
                Math.abs(quake.getLatitude()), latDir, Math.abs(quake.getLongitude()), lonDir));

        // 6. Community Reports Card - Real-time Firestore Listener
        if (mFeltRegistration != null) {
            mFeltRegistration.remove();
        }
        mFeltRegistration = FeltRepository.getInstance().listenCount(quake.getId(), (count, userHasFelt) -> {
            if (mBinding == null) return;
            updateFeltUi(count, userHasFelt);
        });

        mBinding.btnFeltToggle.setOnClickListener(v -> {
            mBinding.btnFeltToggle.setEnabled(false);
            FeltRepository.getInstance().toggleFelt(quake.getId(), new FeltRepository.ToggleCallback() {
                @Override
                public void onSuccess(boolean isNowFelt) {
                    if (mBinding == null) return;
                    mBinding.btnFeltToggle.setEnabled(true);
                    Toast.makeText(QuakeDetailActivity.this,
                            isNowFelt ? "Report submitted! Stay safe." : "Report removed.",
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    if (mBinding == null) return;
                    mBinding.btnFeltToggle.setEnabled(true);
                    Toast.makeText(QuakeDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 7. AI Explanation with SQLite caching & regeneration
        QuakeRepository.getInstance(this).getAiExplanation(quake.getId(), cached -> {
            if (mBinding == null) return;
            if (cached != null && !cached.trim().isEmpty()) {
                mBinding.tvAiExplanationContent.setText(cached);
                mBinding.btnExplainQuake.setText("Explain again");
                mBinding.btnExplainQuake.setEnabled(true);
            }
        });

        mBinding.btnExplainQuake.setOnClickListener(v -> {
            mBinding.btnExplainQuake.setEnabled(false);
            mBinding.btnExplainQuake.setText("Explaining...");
            mBinding.tvAiExplanationContent.setText("Consulting seismic intelligence with Gemini...");

            Location currentLoc = QuakeRepository.getInstance(QuakeDetailActivity.this).getUserLocation();
            String distStr = currentLoc != null
                    ? String.format(Locale.US, "%.0f", GeoUtils.distanceKm(currentLoc.getLatitude(), currentLoc.getLongitude(), quake.getLatitude(), quake.getLongitude()))
                    : "unknown";

            String prompt = String.format(Locale.US,
                    "You are an earthquake safety assistant in a mobile app. Provide a freshly worded explanation of this earthquake to an ordinary person in plain text with no markdown symbols. Use exactly three parts, each starting with its label on its own line:\n" +
                            "What happened:\n" +
                            "How it may feel:\n" +
                            "What to do:\n" +
                            "Keep the whole answer under 120 words. For \"What to do\" give 3 short lines starting with \"- \". Do not predict future earthquakes. This is general information, not an official warning.\n\n" +
                            "Earthquake: magnitude %.1f (%s), depth %.1f km, location \"%s\", %s, distance from the user: %s km. Timestamp: %d",
                    quake.getMagnitude(), quake.getMagType(), quake.getDepthKm(), quake.getPlace(),
                    TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis()), distStr, System.currentTimeMillis());

            GeminiClient.getInstance().generateExplanation(prompt, new GeminiClient.ExplanationCallback() {
                @Override
                public void onSuccess(String explanation) {
                    if (mBinding == null) return;
                    mBinding.tvAiExplanationContent.setText(explanation);
                    mBinding.btnExplainQuake.setText("Explain again");
                    mBinding.btnExplainQuake.setEnabled(true);
                    // Cache explanation in SQLite so subsequent opens load instantly offline
                    QuakeRepository.getInstance(QuakeDetailActivity.this).saveAiExplanation(quake.getId(), explanation);
                }

                @Override
                public void onError(String errorMessage) {
                    if (mBinding == null) return;
                    mBinding.btnExplainQuake.setEnabled(true);
                    mBinding.btnExplainQuake.setText("Explain again");
                    Toast.makeText(QuakeDetailActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 8. View on USGS
        mBinding.btnViewOnUsgs.setOnClickListener(v -> openUsgsEventPage(quake.getId()));
    }

    private void updateFeltUi(int count, boolean userHasFelt) {
        if (count == 0) {
            mBinding.tvFeltCount.setText("No community reports yet. Did you feel this tremor?");
        } else if (count == 1) {
            mBinding.tvFeltCount.setText("1 person reported feeling this earthquake");
        } else {
            mBinding.tvFeltCount.setText(String.format(Locale.US, "%d people reported feeling this earthquake", count));
        }

        if (userHasFelt) {
            mBinding.btnFeltToggle.setText("You reported feeling this");
            mBinding.btnFeltToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.qw_primary));
            mBinding.btnFeltToggle.setTextColor(ContextCompat.getColor(this, R.color.qw_on_primary));
            mBinding.btnFeltToggle.setIconTint(ContextCompat.getColorStateList(this, R.color.qw_on_primary));
        } else {
            mBinding.btnFeltToggle.setText("I felt this");
            mBinding.btnFeltToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            mBinding.btnFeltToggle.setTextColor(ContextCompat.getColor(this, R.color.qw_primary));
            mBinding.btnFeltToggle.setIconTint(ContextCompat.getColorStateList(this, R.color.qw_primary));
        }
    }

    private void positionScaleNeedle(double magnitude) {
        // Clamp scale display between M2.0 and M8.0
        double clamped = Math.max(2.0, Math.min(8.0, magnitude));
        double fraction = (clamped - 2.0) / (8.0 - 2.0);

        mBinding.scalePointerContainer.post(() -> {
            if (mBinding == null) return;
            int totalWidth = mBinding.scalePointerContainer.getWidth();
            int needleWidth = mBinding.ivScaleNeedle.getWidth();
            float translation = (float) ((totalWidth - needleWidth) * fraction);
            mBinding.ivScaleNeedle.setTranslationX(translation);
        });
    }

    private void setupMiniMap(Quake quake) {
        mBinding.miniMapView.setTileSource(TileSourceFactory.MAPNIK);
        mBinding.miniMapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mBinding.miniMapView.setMultiTouchControls(false); // Non-interactive mini map

        GeoPoint epicenter = new GeoPoint(quake.getLatitude(), quake.getLongitude());
        mBinding.miniMapView.getController().setZoom(6.2);
        mBinding.miniMapView.getController().setCenter(epicenter);

        Marker marker = new Marker(mBinding.miniMapView);
        marker.setPosition(epicenter);
        marker.setIcon(createEpicenterMarkerDrawable(quake.getMagnitude()));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        mBinding.miniMapView.getOverlays().add(marker);
        mBinding.miniMapView.invalidate();
    }

    private Drawable createEpicenterMarkerDrawable(double magnitude) {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (32 * density);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int colorRes = MagnitudeUtils.severity(magnitude).getColorRes();
        int color = ContextCompat.getColor(this, colorRes);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // White border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint);

        // Severity circle
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

    private void shareQuake() {
        if (mQuake == null) return;

        String shareText = String.format(Locale.US,
                "M%.1f Earthquake: %s\nTime: %s\nDepth: %.0f km\nCoordinates: %.4f, %.4f\nTracked via QuakeWatch",
                mQuake.getMagnitude(),
                mQuake.getPlace(),
                TimeUtils.formatDateTime(mQuake.getTimeMs()),
                mQuake.getDepthKm(),
                mQuake.getLatitude(),
                mQuake.getLongitude()
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Earthquake Report: " + mQuake.getPlace());
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share earthquake via"));
    }

    private void openUsgsEventPage(String quakeId) {
        String url = "https://earthquake.usgs.gov/earthquakes/eventpage/" + quakeId;
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No browser available to open link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) {
            mBinding.miniMapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBinding != null) {
            mBinding.miniMapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFeltRegistration != null) {
            mFeltRegistration.remove();
            mFeltRegistration = null;
        }
        if (mBinding != null) {
            mBinding.miniMapView.onDetach();
            mBinding = null;
        }
    }
}
