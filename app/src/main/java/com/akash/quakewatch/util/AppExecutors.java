package com.akash.quakewatch.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppExecutors provides shared threading resources across the application.
 * All network and database operations run on the background executor pool,
 * while UI updates and callbacks are posted back to the main thread.
 */
public class AppExecutors {

    private static volatile AppExecutors sInstance;
    private final ExecutorService mDiskIO;
    private final ExecutorService mNetworkIO;
    private final Handler mMainThreadHandler;

    private AppExecutors() {
        mDiskIO = Executors.newSingleThreadExecutor();
        mNetworkIO = Executors.newFixedThreadPool(3);
        mMainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutors.class) {
                if (sInstance == null) {
                    sInstance = new AppExecutors();
                }
            }
        }
        return sInstance;
    }

    public ExecutorService diskIO() {
        return mDiskIO;
    }

    public ExecutorService networkIO() {
        return mNetworkIO;
    }

    public void runOnMain(Runnable runnable) {
        mMainThreadHandler.post(runnable);
    }
}
