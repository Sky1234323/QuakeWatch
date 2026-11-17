package com.akash.quakewatch.analytics;

import com.akash.quakewatch.data.model.Quake;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AnalyticsEngine performs statistical analysis and trend computations on earthquake records.
 * Built as pure Java without Android dependencies, it computes zero-filled daily frequencies,
 * moving averages, magnitude distributions, focal depth categories, and activity shifts.
 */
public class AnalyticsEngine {

    public enum TrendDirection {
        RISING,
        FALLING,
        STABLE
    }

    public static class AnalysisResult {
        public int total;
        public int todayCount;
        public Quake strongest;
        public double avgMagnitude;
        public double avgDepthKm;

        // Zero-filled chronological arrays (oldest to newest)
        public int[] perDay;
        public String[] dayLabels;
        public double[] maxPerDay;
        public double[] movingAvg3;

        // Magnitude bands: [<3.0, 3.0-3.9, 4.0-4.9, 5.0-5.9, >=6.0]
        public int[] magnitudeBands = new int[5];

        // Focal depth bands
        public int shallowCount;      // < 70 km
        public int intermediateCount; // 70 - 300 km
        public int deepCount;         // > 300 km

        // Activity trend (last 7 days vs previous 7 days)
        public double trendPercent;
        public TrendDirection trendDirection;

        // Plain-text formatted summary sentence
        public String summarySentence;
    }

    /**
     * Analyzes the earthquake dataset within the specified time window (in days) relative to nowMs.
     */
    public static AnalysisResult analyze(List<Quake> allQuakes, int windowDays, long nowMs) {
        AnalysisResult res = new AnalysisResult();
        if (windowDays <= 0) {
            windowDays = 7;
        }

        // 1. Calculate local day boundaries for the window (oldest to newest)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStartMs = cal.getTimeInMillis();

        long windowStartMs = todayStartMs - ((long) (windowDays - 1) * 86400000L);

        res.perDay = new int[windowDays];
        res.dayLabels = new String[windowDays];
        res.maxPerDay = new double[windowDays];
        res.movingAvg3 = new double[windowDays];

        SimpleDateFormat labelFormat = new SimpleDateFormat(windowDays <= 7 ? "EEE" : "d MMM", Locale.US);
        long[] dayStartTimestamps = new long[windowDays];

        for (int i = 0; i < windowDays; i++) {
            long dayStart = windowStartMs + ((long) i * 86400000L);
            dayStartTimestamps[i] = dayStart;
            res.dayLabels[i] = labelFormat.format(new Date(dayStart));
            res.maxPerDay[i] = 0.0;
        }

        if (allQuakes == null || allQuakes.isEmpty()) {
            res.total = 0;
            res.todayCount = 0;
            res.strongest = null;
            res.avgMagnitude = 0.0;
            res.avgDepthKm = 0.0;
            res.trendPercent = 0.0;
            res.trendDirection = TrendDirection.STABLE;
            res.summarySentence = String.format(Locale.US, "No earthquakes recorded in the last %d days in this region.", windowDays);
            return res;
        }

        double magSum = 0.0;
        double depthSum = 0.0;

        for (Quake q : allQuakes) {
            long t = q.getTimeMs();

            // Check if quake falls within the overall window
            if (t >= windowStartMs && t <= nowMs) {
                res.total++;
                magSum += q.getMagnitude();
                depthSum += q.getDepthKm();

                // Today count
                if (t >= todayStartMs) {
                    res.todayCount++;
                }

                // Strongest (ties broken by latest time)
                if (res.strongest == null || q.getMagnitude() > res.strongest.getMagnitude()
                        || (q.getMagnitude() == res.strongest.getMagnitude() && q.getTimeMs() > res.strongest.getTimeMs())) {
                    res.strongest = q;
                }

                // Map to zero-filled day bucket
                int dayIndex = (int) ((t - windowStartMs) / 86400000L);
                if (dayIndex >= 0 && dayIndex < windowDays) {
                    res.perDay[dayIndex]++;
                    if (q.getMagnitude() > res.maxPerDay[dayIndex]) {
                        res.maxPerDay[dayIndex] = q.getMagnitude();
                    }
                }

                // Magnitude bands: [<3.0, 3.0-3.9, 4.0-4.9, 5.0-5.9, >=6.0]
                double mag = q.getMagnitude();
                if (mag < 3.0) {
                    res.magnitudeBands[0]++;
                } else if (mag < 4.0) {
                    res.magnitudeBands[1]++;
                } else if (mag < 5.0) {
                    res.magnitudeBands[2]++;
                } else if (mag < 6.0) {
                    res.magnitudeBands[3]++;
                } else {
                    res.magnitudeBands[4]++;
                }

                // Focal depth bands: shallow (<70), intermediate (70-300), deep (>300)
                double depth = q.getDepthKm();
                if (depth < 70.0) {
                    res.shallowCount++;
                } else if (depth <= 300.0) {
                    res.intermediateCount++;
                } else {
                    res.deepCount++;
                }
            }
        }

        if (res.total > 0) {
            res.avgMagnitude = magSum / res.total;
            res.avgDepthKm = depthSum / res.total;
        }

        // Calculate 3-day moving average
        for (int i = 0; i < windowDays; i++) {
            if (i == 0) {
                res.movingAvg3[i] = res.perDay[0];
            } else if (i == 1) {
                res.movingAvg3[i] = (res.perDay[0] + res.perDay[1]) / 2.0;
            } else {
                res.movingAvg3[i] = (res.perDay[i - 2] + res.perDay[i - 1] + res.perDay[i]) / 3.0;
            }
        }

        // 2. Trend calculation: compare last 7 days with the 7 days prior
        long sevenDaysAgoMs = nowMs - (7L * 86400000L);
        long fourteenDaysAgoMs = nowMs - (14L * 86400000L);

        int countLast7 = 0;
        int countPrev7 = 0;

        for (Quake q : allQuakes) {
            long t = q.getTimeMs();
            if (t >= sevenDaysAgoMs && t <= nowMs) {
                countLast7++;
            } else if (t >= fourteenDaysAgoMs && t < sevenDaysAgoMs) {
                countPrev7++;
            }
        }

        if (countPrev7 == 0) {
            if (countLast7 > 0) {
                res.trendPercent = 100.0;
                res.trendDirection = TrendDirection.RISING;
            } else {
                res.trendPercent = 0.0;
                res.trendDirection = TrendDirection.STABLE;
            }
        } else {
            res.trendPercent = ((double) (countLast7 - countPrev7) / countPrev7) * 100.0;
            if (Math.abs(res.trendPercent) < 10.0) {
                res.trendDirection = TrendDirection.STABLE;
            } else if (res.trendPercent >= 10.0) {
                res.trendDirection = TrendDirection.RISING;
            } else {
                res.trendDirection = TrendDirection.FALLING;
            }
        }

        // 3. Generate plain-text activity summary sentence
        res.summarySentence = buildSummarySentence(res, windowDays);

        return res;
    }

    private static String buildSummarySentence(AnalysisResult res, int windowDays) {
        if (res.total == 0) {
            return String.format(Locale.US, "No earthquakes recorded in the last %d days in this region.", windowDays);
        }

        String countClause = String.format(Locale.US, "%d earthquake%s in the last %d days",
                res.total, res.total == 1 ? "" : "s", windowDays);

        String comparisonClause;
        long roundedTrend = Math.round(Math.abs(res.trendPercent));

        if (res.trendDirection == TrendDirection.RISING) {
            comparisonClause = String.format(Locale.US, "%d%% more than the week before", roundedTrend);
        } else if (res.trendDirection == TrendDirection.FALLING) {
            comparisonClause = String.format(Locale.US, "%d%% less than the week before", roundedTrend);
        } else {
            comparisonClause = "similar activity to the week before";
        }

        String strongestClause = "";
        if (res.strongest != null) {
            strongestClause = String.format(Locale.US, " The strongest was M%.1f near %s.",
                    res.strongest.getMagnitude(), res.strongest.getPlace());
        }

        return countClause + ", " + comparisonClause + "." + strongestClause;
    }

    /**
     * Finds the nearest earthquake to the user's coordinates using Haversine distance.
     */
    public static Quake nearest(List<Quake> quakes, double userLat, double userLng) {
        if (quakes == null || quakes.isEmpty()) {
            return null;
        }

        Quake nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Quake q : quakes) {
            double dist = haversine(userLat, userLng, q.getLatitude(), q.getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = q;
            }
        }

        return nearest;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return r * c;
    }
}
