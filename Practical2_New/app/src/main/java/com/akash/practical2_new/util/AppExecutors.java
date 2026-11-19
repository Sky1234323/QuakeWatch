package com.akash.practical2_new.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AppExecutors provides background and main-thread execution facilities
 * to ensure network calls (REST & AI APIs) do not block the UI thread.
 */
public class AppExecutors {

    private static volatile AppExecutors sInstance;
    private final Executor mNetworkExecutor;
    private final Handler mMainThreadHandler;

    private AppExecutors() {
        mNetworkExecutor = Executors.newFixedThreadPool(3);
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

    public void runOnBackground(Runnable runnable) {
        mNetworkExecutor.execute(runnable);
    }

    public void runOnMain(Runnable runnable) {
        mMainThreadHandler.post(runnable);
    }
}
