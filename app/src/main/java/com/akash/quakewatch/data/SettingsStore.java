package com.akash.quakewatch.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SettingsStore manages persistent user preferences using SharedPreferences.
 * It stores flags such as onboarding completion, chosen earthquake region,
 * notification alert thresholds, and the timestamp of the last data sync.
 */
public class SettingsStore {

    private static final String PREF_NAME = "quakewatch_settings";

    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_REGION = "selected_region";
    private static final String KEY_ALERTS_ENABLED = "alerts_enabled";
    private static final String KEY_ALERT_MAGNITUDE = "alert_magnitude";
    private static final String KEY_LAST_UPDATED_MS = "last_updated_ms";
    private static final String KEY_NOTIFIED_IDS = "notified_ids";

    public static final String REGION_SOUTH_ASIA = "South Asia";
    public static final String REGION_WORLDWIDE = "Worldwide";
    public static final String REGION_NEAR_ME = "Near me";

    private static volatile SettingsStore sInstance;
    private final SharedPreferences mPrefs;

    private SettingsStore(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SettingsStore getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SettingsStore.class) {
                if (sInstance == null) {
                    sInstance = new SettingsStore(context);
                }
            }
        }
        return sInstance;
    }

    public boolean isOnboardingCompleted() {
        return mPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        mPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    public String getRegion() {
        return mPrefs.getString(KEY_REGION, REGION_SOUTH_ASIA);
    }

    public void setRegion(String region) {
        mPrefs.edit().putString(KEY_REGION, region).apply();
    }

    public boolean areAlertsEnabled() {
        return mPrefs.getBoolean(KEY_ALERTS_ENABLED, false);
    }

    public void setAlertsEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply();
    }

    public float getAlertMagnitude() {
        return mPrefs.getFloat(KEY_ALERT_MAGNITUDE, 5.0f);
    }

    public void setAlertMagnitude(float mag) {
        mPrefs.edit().putFloat(KEY_ALERT_MAGNITUDE, mag).apply();
    }

    public long getLastUpdatedMs() {
        return mPrefs.getLong(KEY_LAST_UPDATED_MS, 0L);
    }

    public void setLastUpdatedMs(long timestamp) {
        mPrefs.edit().putLong(KEY_LAST_UPDATED_MS, timestamp).apply();
    }

    public Set<String> getNotifiedIds() {
        return mPrefs.getStringSet(KEY_NOTIFIED_IDS, Collections.emptySet());
    }

    public void addNotifiedId(String quakeId) {
        Set<String> current = new HashSet<>(getNotifiedIds());
        current.add(quakeId);
        // Cap at 200 items per spec
        if (current.size() > 200) {
            String oldest = current.iterator().next();
            current.remove(oldest);
        }
        mPrefs.edit().putStringSet(KEY_NOTIFIED_IDS, current).apply();
    }
}
