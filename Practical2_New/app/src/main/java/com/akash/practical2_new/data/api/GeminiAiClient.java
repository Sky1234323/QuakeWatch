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
 * It sends structured QA prompts with strict English language instructions
 * and returns rapid, informative answers for college practical evaluations.
 */
public class GeminiAiClient {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private static final String SYSTEM_INSTRUCTION =
            "You are an intelligent, helpful academic AI assistant for Android & Computer Science students. "
                    + "Always reply strictly in the English language. Keep explanations concise, accurate, and easy to understand.";

    public interface AiCallback {
        void onResponse(String answer, long latencyMs);
        void onError(String errorMessage);
    }

    private final AppExecutors mExecutors;

    public GeminiAiClient() {
        mExecutors = AppExecutors.getInstance();
    }

    /**
     * Sends a question to Gemini and delivers the response on the UI thread.
     */
    public void askQuestion(final String userQuestion, final AiCallback callback) {
        mExecutors.runOnBackground(() -> {
            long startTime = System.currentTimeMillis();
            String apiKey = BuildConfig.GEMINI_API_KEY;

            if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("stub")) {
                mExecutors.runOnMain(() -> callback.onError("Gemini API key is not configured in local.properties."));
                return;
            }

            HttpURLConnection conn = null;
            try {
                URL url = new URL(GEMINI_API_URL + apiKey);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Practical2-GeminiAssistant/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);

                // Build Request JSON
                JSONObject payload = new JSONObject();

                // System Instruction (Strict English)
                JSONObject sysInstruction = new JSONObject();
                JSONArray sysParts = new JSONArray();
                sysParts.put(new JSONObject().put("text", SYSTEM_INSTRUCTION));
                sysInstruction.put("parts", sysParts);
                payload.put("systemInstruction", sysInstruction);

                // User Content
                JSONArray contents = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                JSONArray userParts = new JSONArray();
                userParts.put(new JSONObject().put("text", userQuestion));
                userMsg.put("parts", userParts);
                contents.put(userMsg);
                payload.put("contents", contents);

                // Generation Config
                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.7);
                genConfig.put("maxOutputTokens", 1024);
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
                long latencyMs = System.currentTimeMillis() - startTime;

                if (statusCode >= 200 && statusCode < 300) {
                    String extractedText = parseGeminiText(responseStr);
                    mExecutors.runOnMain(() -> callback.onResponse(extractedText, latencyMs));
                } else {
                    final String err = "Gemini API error (HTTP " + statusCode + "): " + responseStr;
                    mExecutors.runOnMain(() -> callback.onError(err));
                }
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                mExecutors.runOnMain(() -> callback.onError("Network error: " + err));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
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
