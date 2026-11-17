package com.akash.quakewatch.util;

import java.util.Locale;

/**
 * GeoUtils calculates geographic distances between coordinates using the Haversine formula.
 * Earth mean radius is fixed to 6371.0 km per project specification.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /**
     * Calculates the great-circle distance between two latitude/longitude points in kilometers.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                   Math.cos(rLat1) * Math.cos(rLat2) *
                   Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Formats distance in km (e.g. "450 km from you").
     */
    public static String formatDistance(double km) {
        return String.format(Locale.US, "%.0f km from you", km);
    }
}
