package com.akash.quakewatch.ui.home;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.AuthRepository;
import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.data.model.Stats;
import com.akash.quakewatch.databinding.FragmentHomeBinding;
import com.akash.quakewatch.ui.detail.QuakeDetailActivity;
import com.akash.quakewatch.ui.main.MainActivity;
import com.akash.quakewatch.util.GeoUtils;
import com.akash.quakewatch.util.MagnitudeUtils;
import com.akash.quakewatch.util.TimeUtils;
import com.akash.quakewatch.viewmodel.QuakeViewModel;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

/**
 * HomeFragment renders the main earthquake dashboard.
 * It coordinates the hero card, 2x2 activity summary metrics, local time/magnitude filters,
 * offline indicators, and the list of parsed earthquakes inside a SwipeRefreshLayout.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding mBinding;
    private QuakeViewModel mViewModel;
    private QuakeAdapter mAdapter;
    private Quake mCurrentHeroQuake = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentHomeBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(requireActivity()).get(QuakeViewModel.class);

        setupHeader();
        setupRecyclerView();
        setupControls();
        observeViewModel();
    }

    private void setupHeader() {
        mBinding.tvGreeting.setText(TimeUtils.getGreeting());

        FirebaseUser user = AuthRepository.getInstance().getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            if (name != null && !name.trim().isEmpty()) {
                mBinding.tvUserName.setText(name);
                mBinding.tvHeaderAvatar.setText(name.substring(0, 1).toUpperCase());
            } else if (user.getEmail() != null) {
                mBinding.tvUserName.setText(user.getEmail());
                mBinding.tvHeaderAvatar.setText(user.getEmail().substring(0, 1).toUpperCase());
            }
        }

        String region = SettingsStore.getInstance(requireContext()).getRegion();
        mBinding.tvRegionSubtitle.setText(region + " \u2022 Last 30 days");

        mBinding.tvHeaderAvatar.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_profile);
            }
        });
    }

    private void setupRecyclerView() {
        mAdapter = new QuakeAdapter(this::openQuakeDetail);
        mBinding.rvQuakes.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvQuakes.setAdapter(mAdapter);
        mBinding.rvQuakes.setHasFixedSize(false);

        mBinding.swipeRefresh.setColorSchemeResources(R.color.qw_primary);
        mBinding.swipeRefresh.setOnRefreshListener(() -> mViewModel.refresh(true));
    }

    private void setupControls() {
        // Default time window: 7 days
        mBinding.toggleTimeWindow.check(R.id.btn_window_7d);
        mBinding.toggleTimeWindow.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_window_today) {
                mViewModel.setTimeWindow(QuakeViewModel.WINDOW_TODAY);
            } else if (checkedId == R.id.btn_window_7d) {
                mViewModel.setTimeWindow(QuakeViewModel.WINDOW_7_DAYS);
            } else if (checkedId == R.id.btn_window_30d) {
                mViewModel.setTimeWindow(QuakeViewModel.WINDOW_30_DAYS);
            }
        });

        // Filter chips: Recent, M4+, M5+, Felt
        mBinding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_recent) {
                mViewModel.setFilter(QuakeViewModel.FILTER_RECENT);
            } else if (id == R.id.chip_m4) {
                mViewModel.setFilter(QuakeViewModel.FILTER_M4);
            } else if (id == R.id.chip_m5) {
                mViewModel.setFilter(QuakeViewModel.FILTER_M5);
            } else if (id == R.id.chip_felt) {
                mViewModel.setFilter(QuakeViewModel.FILTER_FELT);
            }
        });

        mBinding.btnClearFilters.setOnClickListener(v -> {
            mBinding.chipRecent.setChecked(true);
            mBinding.btnWindow7d.setChecked(true);
        });

        mBinding.btnHeroViewDetails.setOnClickListener(v -> {
            if (mCurrentHeroQuake != null) {
                openQuakeDetail(mCurrentHeroQuake);
            }
        });

        // Quick Action Launchpad navigation
        mBinding.cardActionMap.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_map);
            }
        });

        mBinding.cardActionChat.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_chat);
            }
        });
    }

    private void observeViewModel() {
        mViewModel.getFilteredQuakes().observe(getViewLifecycleOwner(), quakes -> {
            mAdapter.submitList(quakes);
            boolean empty = quakes == null || quakes.isEmpty();
            mBinding.layoutEmpty.setVisibility(empty && !mBinding.swipeRefresh.isRefreshing() ? View.VISIBLE : View.GONE);
            mBinding.rvQuakes.setVisibility(empty ? View.GONE : View.VISIBLE);

            int count = quakes != null ? quakes.size() : 0;
            mBinding.tvCatalogHeader.setText(String.format(Locale.US, "Earthquake Catalog (%d %s)",
                    count, count == 1 ? "event" : "events"));
        });

        mViewModel.getHeroQuake().observe(getViewLifecycleOwner(), this::bindHeroCard);

        mViewModel.getStats().observe(getViewLifecycleOwner(), this::bindStats);

        mViewModel.getFeltCounts().observe(getViewLifecycleOwner(), counts -> {
            mAdapter.setFeltCounts(counts);
        });

        mViewModel.isLoading().observe(getViewLifecycleOwner(), loading -> {
            mBinding.swipeRefresh.setRefreshing(loading);
            boolean hasData = mAdapter.getItemCount() > 0;
            mBinding.layoutSkeletons.setVisibility(loading && !hasData ? View.VISIBLE : View.GONE);
        });

        mViewModel.isOffline().observe(getViewLifecycleOwner(), offline -> {
            if (offline) {
                long lastUpdated = SettingsStore.getInstance(requireContext()).getLastUpdatedMs();
                String timeText = lastUpdated > 0 ? TimeUtils.timeAgo(lastUpdated, System.currentTimeMillis()) : "earlier";
                mBinding.tvOfflineText.setText("You are offline. Showing saved data from " + timeText);
                mBinding.bannerOffline.setVisibility(View.VISIBLE);
            } else {
                mBinding.bannerOffline.setVisibility(View.GONE);
            }
        });

        mViewModel.getUserLocation().observe(getViewLifecycleOwner(), location -> {
            mAdapter.setUserLocation(location);
        });
    }

    private void bindHeroCard(Quake quake) {
        mCurrentHeroQuake = quake;
        if (quake == null) {
            mBinding.cardHero.setVisibility(View.GONE);
            return;
        }

        mBinding.cardHero.setVisibility(View.VISIBLE);
        mBinding.tvHeroBadge.setText(MagnitudeUtils.formatMagnitude(quake.getMagnitude()));

        MagnitudeUtils.Severity sev = MagnitudeUtils.severity(quake.getMagnitude());
        int color = ContextCompat.getColor(requireContext(), sev.getColorRes());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(getResources().getDimension(R.dimen.mag_badge_radius));
        bg.setColor(color);
        mBinding.tvHeroBadge.setBackground(bg);

        mBinding.tvHeroPlace.setText(quake.getPlace());

        String timeAgo = TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis());
        String depth = String.format(Locale.US, "%.1f km deep", quake.getDepthKm());

        android.location.Location userLoc = mViewModel.getUserLocation().getValue();
        if (userLoc != null) {
            double dist = GeoUtils.distanceKm(userLoc.getLatitude(), userLoc.getLongitude(), quake.getLatitude(), quake.getLongitude());
            mBinding.tvHeroDetails.setText(String.format(Locale.US, "%s \u2022 %s \u2022 %s",
                    timeAgo, depth, GeoUtils.formatDistance(dist)));
        } else {
            mBinding.tvHeroDetails.setText(timeAgo + " \u2022 " + depth);
        }
    }

    private void bindStats(Stats stats) {
        if (stats == null) return;

        mBinding.tvStatTodayCount.setText(String.valueOf(stats.getTodayCount()));

        if (stats.getStrongest() != null) {
            mBinding.tvStatStrongest.setText("M" + MagnitudeUtils.formatMagnitude(stats.getStrongest().getMagnitude()));
        } else {
            mBinding.tvStatStrongest.setText("M --");
        }

        if (stats.getTotalCount() > 0) {
            mBinding.tvStatAvgDepth.setText(String.format(Locale.US, "%.0f km", stats.getAvgDepthKm()));
        } else {
            mBinding.tvStatAvgDepth.setText("-- km");
        }

        if (stats.getTrend() == Stats.Trend.RISING) {
            mBinding.ivTrendIcon.setImageResource(R.drawable.ic_trending_up);
            mBinding.ivTrendIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.qw_sev_major));
            mBinding.tvStatActivity.setText(String.format(Locale.US, "+%.0f%%", Math.abs(stats.getTrendPercent())));
        } else if (stats.getTrend() == Stats.Trend.FALLING) {
            mBinding.ivTrendIcon.setImageResource(R.drawable.ic_trending_down);
            mBinding.ivTrendIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.qw_sev_low));
            mBinding.tvStatActivity.setText(String.format(Locale.US, "-%.0f%%", Math.abs(stats.getTrendPercent())));
        } else {
            mBinding.ivTrendIcon.setImageResource(R.drawable.ic_trending_flat);
            mBinding.ivTrendIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.qw_text_secondary));
            mBinding.tvStatActivity.setText("Stable");
        }
    }

    private void openQuakeDetail(Quake quake) {
        Intent intent = new Intent(requireContext(), QuakeDetailActivity.class);
        intent.putExtra(QuakeDetailActivity.EXTRA_QUAKE_ID, quake.getId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
