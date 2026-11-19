package com.akash.practical2_new.data.api;

import com.akash.practical2_new.BuildConfig;
import com.akash.practical2_new.util.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * GeminiAiClient integrates Google Gemini AI API via pure HttpURLConnection.
 * It features automatic multi-model fallback (gemini-3.5-flash-lite, gemini-3.8-flash, etc.)
 * with strict English language instructions and rapid response delivery.
 */
public class GeminiAiClient {

    private static final String[] CANDIDATE_MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.6-flash"
    };

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String SYSTEM_PREFIX =
            "Instructions: You are an academic AI assistant for Android & Computer Science students. "
                    + "Answer strictly in the English language in 2 to 4 concise sentences. Never use Spanish or other languages.\n\nQuestion: ";

    public interface AiCallback {
        void onResponse(String answer, long latencyMs);
        void onError(String errorMessage);
    }

    private final AppExecutors mExecutors;

    public GeminiAiClient() {
        mExecutors = AppExecutors.getInstance();
    }

    /**
     * Sends a question to Gemini and delivers the response on the UI thread,
     * automatically falling back across supported models if needed.
     */
    public void askQuestion(final String userQuestion, final AiCallback callback) {
        mExecutors.runOnBackground(() -> {
            long startTime = System.currentTimeMillis();
            String apiKey = BuildConfig.GEMINI_API_KEY;

            if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("stub")) {
                mExecutors.runOnMain(() -> callback.onError("Gemini API key is not configured in local.properties."));
                return;
            }

            Exception lastException = null;
            String lastErrorResponse = "";

            for (String model : CANDIDATE_MODELS) {
                HttpURLConnection conn = null;
                try {
                    String endpoint = BASE_URL + model + ":generateContent?key=" + apiKey;
                    URL url = new URL(endpoint);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("User-Agent", "Practical2-GeminiAssistant/1.0");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(25000);
                    conn.setDoOutput(true);

                    // Build Request JSON
                    JSONObject payload = new JSONObject();

                    JSONArray contents = new JSONArray();
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");

                    JSONArray userParts = new JSONArray();
                    userParts.put(new JSONObject().put("text", SYSTEM_PREFIX + userQuestion));
                    userMsg.put("parts", userParts);
                    contents.put(userMsg);
                    payload.put("contents", contents);

                    // Generation Config
                    JSONObject genConfig = new JSONObject();
                    genConfig.put("temperature", 0.5);
                    genConfig.put("maxOutputTokens", 400);

                    JSONObject thinking = new JSONObject();
                    thinking.put("thinkingLevel", "minimal");
                    genConfig.put("thinkingConfig", thinking);

                    payload.put("generationConfig", genConfig);

                    byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(postBytes);
                        os.flush();
                    }

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

                    String responseStr = sb.toString();
                    if (statusCode >= 200 && statusCode < 300) {
                        String extractedText = parseGeminiText(responseStr);
                        long latencyMs = System.currentTimeMillis() - startTime;
                        mExecutors.runOnMain(() -> callback.onResponse(extractedText, latencyMs));
                        return; // Successfully answered!
                    } else {
                        lastErrorResponse = "Model " + model + " (HTTP " + statusCode + "): " + responseStr;
                    }
                } catch (Exception e) {
                    lastException = e;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }

            // If all candidate models failed
            final String finalMsg;
            if (lastException != null) {
                finalMsg = "Network error: " + (lastException.getMessage() != null ? lastException.getMessage() : lastException.toString());
            } else {
                finalMsg = !lastErrorResponse.isEmpty() ? lastErrorResponse : "Gemini API failed to generate a response.";
            }
            mExecutors.runOnMain(() -> callback.onError(finalMsg));
        });
    }

    private String parseGeminiText(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject firstCandidate = candidates.getJSONObject(0);
                JSONObject content = firstCandidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text", "No response text.");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Failed to parse Gemini response.";
    }
}
