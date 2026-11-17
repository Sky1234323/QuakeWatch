package com.akash.quakewatch.data.model;

/**
 * Stats encapsulates aggregated metrics for earthquake activity within a given time window,
 * including today's quake count, strongest earthquake, average focal depth,
 * and the percentage comparison against the previous time period.
 */
public class Stats {

    public enum Trend {
        RISING,
        FALLING,
        STABLE
    }

    private final int totalCount;
    private final int todayCount;
    private final Quake strongest;
    private final double avgMagnitude;
    private final double avgDepthKm;
    private final double trendPercent;
    private final Trend trend;

    public Stats(int totalCount, int todayCount, Quake strongest,
                 double avgMagnitude, double avgDepthKm, double trendPercent, Trend trend) {
        this.totalCount = totalCount;
        this.todayCount = todayCount;
        this.strongest = strongest;
        this.avgMagnitude = avgMagnitude;
        this.avgDepthKm = avgDepthKm;
        this.trendPercent = trendPercent;
        this.trend = trend;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getTodayCount() {
        return todayCount;
    }

    public Quake getStrongest() {
        return strongest;
    }

    public double getAvgMagnitude() {
        return avgMagnitude;
    }

    public double getAvgDepthKm() {
        return avgDepthKm;
    }

    public double getTrendPercent() {
        return trendPercent;
    }

    public Trend getTrend() {
        return trend;
    }
}
