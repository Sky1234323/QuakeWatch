package com.akash.quakewatch.data.remote;

import android.location.Location;

import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.util.TimeUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * UsgsClient handles HTTP communication with the official USGS Earthquake Hazards Program API.
 * It builds parameter queries covering the past 30 days based on the selected region preset,
 * executes requests using HttpURLConnection, and delegates response parsing to QuakeMlParser.
 */
public class UsgsClient {

    private static final String BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query";
    private static final String USER_AGENT = "QuakeWatch/1.0 (Android student project)";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final QuakeMlParser mParser;

    public UsgsClient() {
        mParser = new QuakeMlParser();
    }

    /**
     * Fetches earthquake data for the specified region and optional user location.
     */
    public List<Quake> fetchQuakes(String region, Location userLocation) throws Exception {
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - (30L * 24L * 60L * 60L * 1000L);
        String startTimeUtc = TimeUtils.formatIsoUtc(thirtyDaysAgo);

        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("?format=quakeml")
                .append("&starttime=").append(startTimeUtc)
                .append("&orderby=time")
                .append("&limit=400")
                .append("&eventtype=earthquake");

        appendRegionParameters(urlBuilder, region, userLocation);

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("USGS server returned HTTP " + responseCode);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            return mParser.parse(in);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Fetches earthquake data for the specified number of recent days (used by QuakeCheckWorker).
     */
    public List<Quake> fetchRecentDays(String region, int days, Location userLocation) throws Exception {
        long now = System.currentTimeMillis();
        long daysAgo = now - ((long) days * 24L * 60L * 60L * 1000L);
        String startTimeUtc = TimeUtils.formatIsoUtc(daysAgo);

        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("?format=quakeml")
                .append("&starttime=").append(startTimeUtc)
                .append("&orderby=time")
                .append("&limit=200")
                .append("&eventtype=earthquake");

        appendRegionParameters(urlBuilder, region, userLocation);

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("USGS server returned HTTP " + responseCode);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            return mParser.parse(in);
        } finally {
            conn.disconnect();
        }
    }

    private void appendRegionParameters(StringBuilder sb, String region, Location userLocation) {
        if (SettingsStore.REGION_WORLDWIDE.equalsIgnoreCase(region)) {
            sb.append("&minmagnitude=4.5");
        } else if (SettingsStore.REGION_NEAR_ME.equalsIgnoreCase(region) && userLocation != null) {
            sb.append(String.format(Locale.US, "&latitude=%.4f&longitude=%.4f&maxradiuskm=1500&minmagnitude=2.5",
                    userLocation.getLatitude(), userLocation.getLongitude()));
        } else {
            // Default: South Asia
            sb.append("&minlatitude=5&maxlatitude=40&minlongitude=60&maxlongitude=100&minmagnitude=2.5");
        }
    }
}
