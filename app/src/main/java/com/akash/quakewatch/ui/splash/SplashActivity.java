package com.akash.quakewatch.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.AuthRepository;
import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.ui.auth.AuthActivity;
import com.akash.quakewatch.ui.main.MainActivity;
import com.akash.quakewatch.ui.onboarding.OnboardingActivity;

/**
 * SplashActivity presents the QuakeWatch brand for 1.2 seconds.
 * It routes the user to OnboardingActivity if first launch,
 * to AuthActivity if not signed in, or directly to MainActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1200L;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mHandler.postDelayed(this::routeNextScreen, SPLASH_DURATION_MS);
    }

    private void routeNextScreen() {
        if (isFinishing()) {
            return;
        }

        SettingsStore settingsStore = SettingsStore.getInstance(this);
        AuthRepository authRepository = AuthRepository.getInstance();

        Intent intent;
        if (!settingsStore.isOnboardingCompleted()) {
            intent = new Intent(this, OnboardingActivity.class);
        } else if (!authRepository.isSignedIn()) {
            intent = new Intent(this, AuthActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}
