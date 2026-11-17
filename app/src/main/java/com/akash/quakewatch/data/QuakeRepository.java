package com.akash.quakewatch.data;

import android.content.Context;
import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.akash.quakewatch.data.local.QuakeDbHelper;
import com.akash.quakewatch.data.model.ChatMessage;
import com.akash.quakewatch.data.model.ChatSession;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.data.remote.UsgsClient;
import com.akash.quakewatch.util.AppExecutors;
import com.akash.quakewatch.util.NetworkUtils;

import java.util.Collections;
import java.util.List;

/**
 * QuakeRepository coordinates earthquake data access across local SQLite storage and remote USGS services.
 * It implements an offline-first architecture by publishing cached records immediately to LiveData,
 * then debouncing and refreshing network updates on a background executor.
 */
public class QuakeRepository {

    private static final long DEBOUNCE_INTERVAL_MS = 2L * 60L * 1000L; // 2 minutes per spec
    private static volatile QuakeRepository sInstance;

    private final Context mContext;
    private final QuakeDbHelper mDbHelper;
    private final UsgsClient mUsgsClient;
    private final SettingsStore mSettingsStore;
    private final AppExecutors mExecutors;

    private final MutableLiveData<List<Quake>> mQuakesLiveData = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> mLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> mErrorLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> mOfflineLiveData = new MutableLiveData<>(false);

    private Location mCachedUserLocation = null;

    public static QuakeRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (QuakeRepository.class) {
                if (sInstance == null) {
                    sInstance = new QuakeRepository(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private QuakeRepository(Context context) {
        mContext = context;
        mDbHelper = QuakeDbHelper.getInstance(context);
        mUsgsClient = new UsgsClient();
        mSettingsStore = SettingsStore.getInstance(context);
        mExecutors = AppExecutors.getInstance();

        // Load existing cache into LiveData on startup
        loadFromCache();
    }

    public LiveData<List<Quake>> getQuakes() {
        return mQuakesLiveData;
    }

    public LiveData<Boolean> isLoading() {
        return mLoadingLiveData;
    }

    public LiveData<String> getError() {
        return mErrorLiveData;
    }

    public LiveData<Boolean> isOffline() {
        return mOfflineLiveData;
    }

    public void setUserLocation(Location location) {
        this.mCachedUserLocation = location;
    }

    public Location getUserLocation() {
        return mCachedUserLocation;
    }

    public interface QuakeCallback {
        void onLoaded(Quake quake);
    }

    /**
     * Queries a single earthquake by its ID on a background diskIO thread.
     */
    public void getQuakeById(String id, QuakeCallback callback) {
        mExecutors.diskIO().execute(() -> {
            Quake quake = mDbHelper.getQuakeById(id);
            mExecutors.runOnMain(() -> {
                if (callback != null) {
                    callback.onLoaded(quake);
                }
            });
        });
    }

    public interface ExplanationCallback {
        void onLoaded(String explanation);
    }

    public interface RepositoryCallback<T> {
        void onLoaded(T result);
    }

    /**
     * Saves a chat session and message asynchronously to local SQLite.
     */
    public void saveChatMessage(String sessionId, ChatMessage message, String sessionTitle) {
        mExecutors.diskIO().execute(() -> {
            mDbHelper.createOrUpdateSession(sessionId, sessionTitle, message.getTimestampMs());
            mDbHelper.saveChatMessage(sessionId, message);
        });
    }

    /**
     * Loads all persistent chat sessions on diskIO thread and posts to main thread.
     */
    public void loadChatSessions(RepositoryCallback<List<ChatSession>> callback) {
        mExecutors.diskIO().execute(() -> {
            List<ChatSession> sessions = mDbHelper.getAllSessions();
            mExecutors.runOnMain(() -> {
                if (callback != null) {
                    callback.onLoaded(sessions);
                }
            });
        });
    }

    /**
     * Loads all messages for a specific session on diskIO thread.
     */
    public void loadSessionMessages(String sessionId, RepositoryCallback<List<ChatMessage>> callback) {
        mExecutors.diskIO().execute(() -> {
            List<ChatMessage> messages = mDbHelper.getMessagesForSession(sessionId);
            mExecutors.runOnMain(() -> {
                if (callback != null) {
                    callback.onLoaded(messages);
                }
            });
        });
    }

    /**
     * Deletes a chat session and its messages asynchronously.
     */
    public void deleteChatSession(String sessionId, Runnable onComplete) {
        mExecutors.diskIO().execute(() -> {
            mDbHelper.deleteSession(sessionId);
            if (onComplete != null) {
                mExecutors.runOnMain(onComplete);
            }
        });
    }

    /**
     * Reads a cached AI explanation from SQLite on a background thread.
     */
    public void getAiExplanation(String quakeId, ExplanationCallback callback) {
        mExecutors.diskIO().execute(() -> {
            String explanation = mDbHelper.getAiExplanation(quakeId);
            mExecutors.runOnMain(() -> {
                if (callback != null) {
                    callback.onLoaded(explanation);
                }
            });
        });
    }

    /**
     * Caches a generated AI explanation into SQLite.
     */
    public void saveAiExplanation(String quakeId, String explanation) {
        mExecutors.diskIO().execute(() -> {
            mDbHelper.saveAiExplanation(quakeId, explanation);
        });
    }

    /**
     * Loads saved earthquakes from the local SQLite database.
     */
    public void loadFromCache() {
        mExecutors.diskIO().execute(() -> {
            List<Quake> cached = mDbHelper.getAllQuakes();
            mExecutors.runOnMain(() -> mQuakesLiveData.setValue(cached));
        });
    }

    /**
     * Refreshes data from the USGS API. If force is false, respects the 2-minute debounce rule.
     */
    public void refresh(boolean force) {
        long now = System.currentTimeMillis();
        long lastUpdate = mSettingsStore.getLastUpdatedMs();

        if (!force && (now - lastUpdate < DEBOUNCE_INTERVAL_MS)) {
            // Data is still fresh, do not refetch unless user pulled to refresh
            return;
        }

        boolean online = NetworkUtils.isOnline(mContext);
        mOfflineLiveData.setValue(!online);

        if (!online) {
            // When offline, ensure local cache is loaded and notify
            loadFromCache();
            return;
        }

        mLoadingLiveData.setValue(true);
        mErrorLiveData.setValue(null);

        String currentRegion = mSettingsStore.getRegion();

        mExecutors.networkIO().execute(() -> {
            try {
                List<Quake> remoteQuakes = mUsgsClient.fetchQuakes(currentRegion, mCachedUserLocation);

                // Save to SQLite cache and purge entries older than 30 days
                mDbHelper.saveQuakes(remoteQuakes);
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
                mDbHelper.deleteOlderThan(thirtyDaysAgo);

                // Update timestamp in SettingsStore
                mSettingsStore.setLastUpdatedMs(System.currentTimeMillis());

                // Read full sorted dataset from DB to ensure local consistency
                List<Quake> updatedList = mDbHelper.getAllQuakes();

                mExecutors.runOnMain(() -> {
                    mQuakesLiveData.setValue(updatedList);
                    mLoadingLiveData.setValue(false);
                    mOfflineLiveData.setValue(false);
                });
            } catch (Exception e) {
                mExecutors.runOnMain(() -> {
                    mLoadingLiveData.setValue(false);
                    // On failure, rely on existing cached quakes and report friendly message
                    mErrorLiveData.setValue("Unable to load latest quakes. Showing saved data.");
                    loadFromCache();
                });
            }
        });
    }

    /**
     * Clears local cache and fetches fresh data (used when region setting is changed).
     */
    public void onRegionChanged() {
        mExecutors.diskIO().execute(() -> {
            mDbHelper.clearAllQuakes();
            mExecutors.runOnMain(() -> {
                mQuakesLiveData.setValue(Collections.emptyList());
                refresh(true);
            });
        });
    }
}
