package com.akash.quakewatch.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * TimeUtils handles all date and time conversions across the application.
 * It parses USGS ISO-8601 UTC timestamps with fractions without relying on java.time,
 * formats relative time-ago strings, and calculates local day boundaries.
 */
public final class TimeUtils {

    private TimeUtils() {}

    /**
     * Parses an ISO-8601 UTC string (e.g. 2026-11-13T12:12:57.241Z) into epoch milliseconds.
     * Uses the first 19 characters with a UTC SimpleDateFormat and extracts fraction digits as ms.
     */
    public static long parseIsoUtc(String isoString) throws ParseException {
        if (isoString == null || isoString.length() < 19) {
            throw new ParseException("Invalid ISO string: " + isoString, 0);
        }

        String base = isoString.substring(0, 19);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = sdf.parse(base);
        long epochMs = date != null ? date.getTime() : 0L;

        // Parse optional fraction digits
        int dotIndex = isoString.indexOf('.');
        int zIndex = isoString.indexOf('Z');
        if (zIndex < 0) {
            zIndex = isoString.length();
        }

        if (dotIndex >= 0 && dotIndex < zIndex) {
            String fractionStr = isoString.substring(dotIndex + 1, zIndex);
            if (!fractionStr.isEmpty()) {
                double fraction = Double.parseDouble("0." + fractionStr);
                epochMs += Math.round(fraction * 1000.0);
            }
        }

        return epochMs;
    }

    /**
     * Formats epoch ms into USGS query format (yyyy-MM-dd'T'HH:mm:ss) in UTC.
     */
    public static String formatIsoUtc(long timeMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timeMs));
    }

    /**
     * Formats a relative time description (e.g. "just now", "5 min ago", "3 h ago", "2 days ago").
     */
    public static String timeAgo(long eventMs, long nowMs) {
        long diffMs = nowMs - eventMs;
        if (diffMs < 0) diffMs = 0;

        long minutes = diffMs / 60000L;
        long hours = diffMs / 3600000L;
        long days = diffMs / 86400000L;

        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (hours < 24) {
            return hours + " h ago";
        } else if (days == 1) {
            return "1 day ago";
        } else {
            return days + " days ago";
        }
    }

    /**
     * Returns a full readable date and time string in local time.
     */
    public static String formatDateTime(long timeMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault());
        return sdf.format(new Date(timeMs));
    }

    /**
     * Returns a time-of-day greeting (Good morning, Good afternoon, Good evening).
     */
    public static String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Good morning";
        } else if (hour < 17) {
            return "Good afternoon";
        } else {
            return "Good evening";
        }
    }

    /**
     * Returns the epoch millisecond of local midnight for today.
     */
    public static long startOfTodayMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
