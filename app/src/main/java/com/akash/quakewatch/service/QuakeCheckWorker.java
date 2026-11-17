package com.akash.quakewatch.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.data.local.QuakeDbHelper;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.data.remote.UsgsClient;
import com.akash.quakewatch.util.NotificationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * QuakeCheckWorker executes background polling every 15 minutes to discover significant
 * earthquakes matching the user's alert threshold and triggers priority notifications.
 */
public class QuakeCheckWorker extends Worker {

    public static final String WORK_NAME = "QuakeCheckWorker";

    public QuakeCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Schedules the periodic background worker with WorkManager.
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(QuakeCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    /**
     * Cancels any scheduled background checks.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SettingsStore settings = SettingsStore.getInstance(context);

        // If alerts disabled or permission not granted, finish cleanly
        if (!settings.areAlertsEnabled() || !NotificationHelper.hasPermission(context)) {
            return Result.success();
        }

        try {
            UsgsClient client = new UsgsClient();
            String region = settings.getRegion();

            // Fetch quakes from the last 2 days per Section 12
            List<Quake> recentQuakes = client.fetchRecentDays(region, 2, null);
            if (recentQuakes == null || recentQuakes.isEmpty()) {
                return Result.success();
            }

            // Cache quakes locally
            QuakeDbHelper.getInstance(context).saveQuakes(recentQuakes);

            float threshold = settings.getAlertMagnitude();
            Set<String> notifiedIds = settings.getNotifiedIds();

            // Filter quakes at or above threshold that were not notified yet
            List<Quake> eligible = new ArrayList<>();
            for (Quake quake : recentQuakes) {
                if (quake.getMagnitude() >= threshold && !notifiedIds.contains(quake.getId())) {
                    eligible.add(quake);
                }
            }

            if (eligible.isEmpty()) {
                return Result.success();
            }

            // Sort eligible quakes by magnitude descending (strongest first)
            Collections.sort(eligible, (q1, q2) -> Double.compare(q2.getMagnitude(), q1.getMagnitude()));

            // Post notifications for up to 3 new quakes per run
            int count = Math.min(3, eligible.size());
            for (int i = 0; i < count; i++) {
                Quake target = eligible.get(i);
                NotificationHelper.showQuakeAlert(context, target);
                settings.addNotifiedId(target.getId());
            }

            return Result.success();
        } catch (Exception e) {
            // Worker retry on network failure
            return Result.retry();
        }
    }
}
