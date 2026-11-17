package com.akash.quakewatch.viewmodel;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.akash.quakewatch.analytics.AnalyticsEngine;
import com.akash.quakewatch.data.FeltRepository;
import com.akash.quakewatch.data.QuakeRepository;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.data.model.Stats;
import com.akash.quakewatch.util.TimeUtils;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QuakeViewModel provides shared earthquake data and filtering logic
 * to HomeFragment, MapFragment, and InsightsFragment.
 * It manages local time window intervals, magnitude filtering, and summary statistics.
 */
public class QuakeViewModel extends AndroidViewModel {

    public static final int WINDOW_TODAY = 0;
    public static final int WINDOW_7_DAYS = 1;
    public static final int WINDOW_30_DAYS = 2;

    public static final int FILTER_RECENT = 0;
    public static final int FILTER_M4 = 1;
    public static final int FILTER_M5 = 2;
    public static final int FILTER_FELT = 3;

    private final QuakeRepository mRepository;

    private final MutableLiveData<Integer> mTimeWindow = new MutableLiveData<>(WINDOW_7_DAYS);
    private final MutableLiveData<Integer> mFilter = new MutableLiveData<>(FILTER_RECENT);
    private final MutableLiveData<Location> mUserLocation = new MutableLiveData<>(null);
    private final MutableLiveData<Map<String, Integer>> mFeltCounts = new MutableLiveData<>(Collections.emptyMap());

    private final MediatorLiveData<List<Quake>> mFilteredQuakes = new MediatorLiveData<>();
    private final MediatorLiveData<Quake> mHeroQuake = new MediatorLiveData<>();
    private final MediatorLiveData<Stats> mStats = new MediatorLiveData<>();

    private ListenerRegistration mFeltRegistration;

    public QuakeViewModel(@NonNull Application application) {
        super(application);
        mRepository = QuakeRepository.getInstance(application);

        mFilteredQuakes.addSource(mRepository.getQuakes(), quakes -> applyFilters());
        mFilteredQuakes.addSource(mTimeWindow, window -> applyFilters());
        mFilteredQuakes.addSource(mFilter, filter -> applyFilters());
        mFilteredQuakes.addSource(mFeltCounts, counts -> applyFilters());

        // Listen for live community felt counts from Firestore
        mFeltRegistration = FeltRepository.getInstance().listenAllCounts(counts -> {
            mFeltCounts.setValue(counts);
        });

        // Refresh initially
        mRepository.refresh(false);
    }

    public LiveData<List<Quake>> getFilteredQuakes() {
        return mFilteredQuakes;
    }

    public LiveData<List<Quake>> getAllQuakes() {
        return mRepository.getQuakes();
    }

    public LiveData<Quake> getHeroQuake() {
        return mHeroQuake;
    }

    public LiveData<Stats> getStats() {
        return mStats;
    }

    public LiveData<Map<String, Integer>> getFeltCounts() {
        return mFeltCounts;
    }

    public LiveData<Boolean> isLoading() {
        return mRepository.isLoading();
    }

    public LiveData<Boolean> isOffline() {
        return mRepository.isOffline();
    }

    public LiveData<String> getError() {
        return mRepository.getError();
    }

    public LiveData<Integer> getTimeWindow() {
        return mTimeWindow;
    }

    public LiveData<Integer> getFilter() {
        return mFilter;
    }

    public LiveData<Location> getUserLocation() {
        return mUserLocation;
    }

    public void setTimeWindow(int window) {
        if (mTimeWindow.getValue() == null || mTimeWindow.getValue() != window) {
            mTimeWindow.setValue(window);
        }
    }

    public void setFilter(int filter) {
        if (mFilter.getValue() == null || mFilter.getValue() != filter) {
            mFilter.setValue(filter);
        }
    }

    public void setUserLocation(Location location) {
        mUserLocation.setValue(location);
        mRepository.setUserLocation(location);
    }

    public void refresh(boolean force) {
        mRepository.refresh(force);
    }

    private void applyFilters() {
        List<Quake> allQuakes = mRepository.getQuakes().getValue();
        if (allQuakes == null || allQuakes.isEmpty()) {
            mFilteredQuakes.setValue(Collections.emptyList());
            mHeroQuake.setValue(null);
            mStats.setValue(new Stats(0, 0, null, 0.0, 0.0, 0.0, Stats.Trend.STABLE));
            return;
        }

        // Hero quake is always the latest earthquake overall
        mHeroQuake.setValue(allQuakes.get(0));

        int window = mTimeWindow.getValue() != null ? mTimeWindow.getValue() : WINDOW_7_DAYS;
        int filter = mFilter.getValue() != null ? mFilter.getValue() : FILTER_RECENT;

        long now = System.currentTimeMillis();
        long startTime;

        if (window == WINDOW_TODAY) {
            startTime = TimeUtils.startOfTodayMs();
        } else if (window == WINDOW_7_DAYS) {
            startTime = now - (7L * 24L * 60L * 60L * 1000L);
        } else {
            startTime = now - (30L * 24L * 60L * 60L * 1000L);
        }

        List<Quake> timeFiltered = new ArrayList<>();
        for (Quake q : allQuakes) {
            if (q.getTimeMs() >= startTime) {
                timeFiltered.add(q);
            }
        }

        // Compute statistics for the selected time window
        computeStats(timeFiltered, allQuakes, now);

        // Apply magnitude / category chip filters
        List<Quake> result = new ArrayList<>();
        Map<String, Integer> feltMap = mFeltCounts.getValue() != null ? mFeltCounts.getValue() : Collections.emptyMap();

        if (filter == FILTER_FELT) {
            // When filtering by community felt reports, check across all cached earthquakes (up to 30 days)
            // so felt events aren't hidden if the time toggle was set to Today or 7 days
            for (Quake q : allQuakes) {
                if (feltMap.getOrDefault(q.getId(), 0) > 0) {
                    result.add(q);
                }
            }
        } else {
            for (Quake q : timeFiltered) {
                if (filter == FILTER_M4 && q.getMagnitude() < 4.0) continue;
                if (filter == FILTER_M5 && q.getMagnitude() < 5.0) continue;
                result.add(q);
            }
        }

        mFilteredQuakes.setValue(result);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mFeltRegistration != null) {
            mFeltRegistration.remove();
            mFeltRegistration = null;
        }
    }

    private void computeStats(List<Quake> windowQuakes, List<Quake> allQuakes, long now) {
        int window = mTimeWindow.getValue() != null ? mTimeWindow.getValue() : WINDOW_7_DAYS;
        int windowDays = 7;
        if (window == WINDOW_TODAY) {
            windowDays = 1;
        } else if (window == WINDOW_30_DAYS) {
            windowDays = 30;
        }

        AnalyticsEngine.AnalysisResult res = AnalyticsEngine.analyze(allQuakes, windowDays, now);

        Stats.Trend trend;
        if (res.trendDirection == AnalyticsEngine.TrendDirection.RISING) {
            trend = Stats.Trend.RISING;
        } else if (res.trendDirection == AnalyticsEngine.TrendDirection.FALLING) {
            trend = Stats.Trend.FALLING;
        } else {
            trend = Stats.Trend.STABLE;
        }

        mStats.setValue(new Stats(res.total, res.todayCount, res.strongest, res.avgMagnitude, res.avgDepthKm, res.trendPercent, trend));
    }
}
