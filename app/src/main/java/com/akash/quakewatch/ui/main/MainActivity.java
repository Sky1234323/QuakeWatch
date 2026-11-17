package com.akash.quakewatch.ui.main;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.akash.quakewatch.R;
import com.akash.quakewatch.databinding.ActivityMainBinding;
import com.akash.quakewatch.ui.chat.ChatFragment;
import com.akash.quakewatch.ui.home.HomeFragment;
import com.akash.quakewatch.ui.insights.InsightsFragment;
import com.akash.quakewatch.ui.map.MapFragment;
import com.akash.quakewatch.ui.profile.ProfileFragment;
import com.akash.quakewatch.util.NotificationHelper;

/**
 * MainActivity serves as the main host container for the application.
 * It manages the bottom navigation bar and coordinates the five primary fragments:
 * Home, Map, Insights, Ask AI, and Profile, preserving each fragment's state using hide/show.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding mBinding;
    private final Fragment mHomeFragment = new HomeFragment();
    private final Fragment mMapFragment = new MapFragment();
    private final Fragment mInsightsFragment = new InsightsFragment();
    private final Fragment mChatFragment = new ChatFragment();
    private final Fragment mProfileFragment = new ProfileFragment();

    private Fragment mActiveFragment = mHomeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.mainRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            mBinding.bottomNav.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // Initialize high-priority earthquake alerts notification channel
        NotificationHelper.createNotificationChannel(this);

        setupFragments(savedInstanceState);
        setupBottomNavigation();
    }

    private void setupFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            fm.beginTransaction()
                    .add(R.id.fragment_container, mProfileFragment, "profile").hide(mProfileFragment)
                    .add(R.id.fragment_container, mChatFragment, "chat").hide(mChatFragment)
                    .add(R.id.fragment_container, mInsightsFragment, "insights").hide(mInsightsFragment)
                    .add(R.id.fragment_container, mMapFragment, "map").hide(mMapFragment)
                    .add(R.id.fragment_container, mHomeFragment, "home")
                    .commit();
            mActiveFragment = mHomeFragment;
        } else {
            // Restore fragment references
            Fragment home = fm.findFragmentByTag("home");
            Fragment map = fm.findFragmentByTag("map");
            Fragment insights = fm.findFragmentByTag("insights");
            Fragment chat = fm.findFragmentByTag("chat");
            Fragment profile = fm.findFragmentByTag("profile");

            if (home != null && map != null && insights != null && chat != null && profile != null) {
                // If restored, identify active fragment
                if (!home.isHidden()) mActiveFragment = home;
                else if (!map.isHidden()) mActiveFragment = map;
                else if (!insights.isHidden()) mActiveFragment = insights;
                else if (!chat.isHidden()) mActiveFragment = chat;
                else if (!profile.isHidden()) mActiveFragment = profile;
            }
        }
    }

    private void setupBottomNavigation() {
        mBinding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(mHomeFragment);
                return true;
            } else if (itemId == R.id.nav_map) {
                switchFragment(mMapFragment);
                return true;
            } else if (itemId == R.id.nav_insights) {
                switchFragment(mInsightsFragment);
                return true;
            } else if (itemId == R.id.nav_chat) {
                switchFragment(mChatFragment);
                return true;
            } else if (itemId == R.id.nav_profile) {
                switchFragment(mProfileFragment);
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment targetFragment) {
        if (mActiveFragment == targetFragment) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .hide(mActiveFragment)
                .show(targetFragment)
                .commit();
        mActiveFragment = targetFragment;
    }

    public void selectTab(int navItemId) {
        mBinding.bottomNav.setSelectedItemId(navItemId);
    }
}
