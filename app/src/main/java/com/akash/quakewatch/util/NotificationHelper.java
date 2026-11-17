package com.akash.quakewatch.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.local.QuakeDbHelper;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.ui.detail.QuakeDetailActivity;

import java.util.List;
import java.util.Locale;

/**
 * NotificationHelper manages system notification channels, permission validation,
 * and builds interactive high-priority alerts for significant earthquake events.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID = "quake_alerts";
    public static final String CHANNEL_NAME = "Earthquake alerts";

    /**
     * Registers the high-priority notification channel with the Android system on API 26+.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("High-priority alerts for significant earthquakes exceeding user magnitude threshold");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Checks whether the application has permission to post notifications.
     */
    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Displays a high-priority earthquake alert notification. Tapping opens QuakeDetailActivity.
     */
    public static void showQuakeAlert(Context context, Quake quake) {
        if (context == null || quake == null || !hasPermission(context)) {
            return;
        }

        createNotificationChannel(context);

        Intent intent = new Intent(context, QuakeDetailActivity.class);
        intent.putExtra(QuakeDetailActivity.EXTRA_QUAKE_ID, quake.getId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                quake.getId().hashCode(),
                intent,
                flags
        );

        String title = String.format(Locale.US, "M%.1f earthquake", quake.getMagnitude());
        String body = String.format(Locale.US, "%s | %s | depth %.0f km",
                quake.getPlace(),
                TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis()),
                quake.getDepthKm());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setColor(ContextCompat.getColor(context, R.color.qw_primary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(quake.getId().hashCode(), builder.build());
        } catch (SecurityException ignored) {
        }
    }

    /**
     * Dispatches an immediate test notification for live demonstration in college evaluations.
     */
    public static void showTestAlert(Context context) {
        if (context == null) return;

        // Try to pick an existing cached quake so tapping takes the evaluator to real data
        List<Quake> quakes = QuakeDbHelper.getInstance(context).getAllQuakes();
        Quake targetQuake = null;
        if (quakes != null && !quakes.isEmpty()) {
            targetQuake = quakes.get(0);
        }

        if (targetQuake == null) {
            targetQuake = new Quake(
                    "demo_test_alert",
                    "43 km S of Jurm, Afghanistan",
                    5.4,
                    "mb",
                    202.7,
                    36.50,
                    71.05,
                    System.currentTimeMillis() - 1000 * 60 * 12
            );
            // Save sample quake in DB so QuakeDetailActivity can load it when tapped
            QuakeDbHelper.getInstance(context).saveQuakes(java.util.Collections.singletonList(targetQuake));
        }

        showQuakeAlert(context, targetQuake);
    }
}
