package com.akash.practical2_new.data.api;

import com.akash.practical2_new.data.model.ApiItem;
import com.akash.practical2_new.util.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RestApiClient performs standard HTTP GET requests using Java HttpURLConnection
 * to fetch JSON data from a REST API endpoint and parses the response.
 */
public class RestApiClient {

    public static final String ENDPOINT_JSONPLACEHOLDER = "https://jsonplaceholder.typicode.com/posts";
    public static final String ENDPOINT_USGS = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";

    public interface ApiCallback {
        void onSuccess(List<ApiItem> items, String rawJson, int statusCode, long durationMs);
        void onError(String errorMessage);
    }

    private final AppExecutors mExecutors;

    public RestApiClient() {
        mExecutors = AppExecutors.getInstance();
    }

    /**
     * Fetches data asynchronously from the given REST API endpoint.
     */
    public void fetchData(final String endpointUrl, final ApiCallback callback) {
        mExecutors.runOnBackground(() -> {
            long startTime = System.currentTimeMillis();
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpointUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "QuakeWatch-Practical2/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                }

                String rawResponse = sb.toString();
                long durationMs = System.currentTimeMillis() - startTime;

                if (statusCode >= 200 && statusCode < 300) {
                    List<ApiItem> parsedItems = parseJsonResponse(endpointUrl, rawResponse);
                    mExecutors.runOnMain(() -> callback.onSuccess(parsedItems, rawResponse, statusCode, durationMs));
                } else {
                    final String err = "HTTP " + statusCode + ": " + rawResponse;
                    mExecutors.runOnMain(() -> callback.onError(err));
                }
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                mExecutors.runOnMain(() -> callback.onError("Network error: " + msg));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private List<ApiItem> parseJsonResponse(String endpointUrl, String rawJson) throws Exception {
        List<ApiItem> list = new ArrayList<>();
        String trimmed = rawJson.trim();

        if (trimmed.startsWith("[")) {
            // JSON Array (e.g. JSONPlaceholder /posts)
            JSONArray array = new JSONArray(trimmed);
            int count = Math.min(array.length(), 25);
            for (int i = 0; i < count; i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = String.valueOf(obj.optInt("id", i + 1));
                String title = obj.optString("title", "Untitled");
                String body = obj.optString("body", "");
                list.add(new ApiItem(id, title, body, "Post #" + id));
            }
        } else if (trimmed.startsWith("{")) {
            // JSON Object (e.g. GeoJSON features)
            JSONObject root = new JSONObject(trimmed);
            if (root.has("features")) {
                JSONArray features = root.getJSONArray("features");
                int count = Math.min(features.length(), 25);
                for (int i = 0; i < count; i++) {
                    JSONObject feat = features.getJSONObject(i);
                    String id = feat.optString("id", "item-" + i);
                    JSONObject props = feat.optJSONObject("properties");
                    if (props != null) {
                        double mag = props.optDouble("mag", 0.0);
                        String place = props.optString("place", "Unknown Location");
                        String title = "M " + String.format(java.util.Locale.US, "%.1f", mag) + " - " + place;
                        String body = "Status: " + props.optString("status", "reviewed")
                                + " | Felt reports: " + props.optInt("felt", 0);
                        list.add(new ApiItem(id, title, body, "Magnitude " + mag));
                    }
                }
            }
        }
        return list;
    }
}
