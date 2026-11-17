package com.akash.quakewatch.ai;

import android.os.Handler;
import android.os.Looper;

import com.akash.quakewatch.BuildConfig;
import com.akash.quakewatch.data.model.ChatMessage;
import com.akash.quakewatch.util.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GeminiClient orchestrates requests to the Google Gemini Generative AI endpoints.
 * It provides plain-language earthquake explanations and streaming conversational chat
 * with automatic model fallback, SSE chunk processing, and a strict 30-second deadline.
 */
public class GeminiClient {

    public static final String[] MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.6-flash"
    };

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final long TOTAL_DEADLINE_MS = 30000L; // 30-second hard cap per spec

    private static volatile GeminiClient sInstance;

    private final AppExecutors mExecutors;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface ExplanationCallback {
        void onSuccess(String explanation);
        void onError(String errorMessage);
    }

    public interface StreamCallback {
        void onChunk(String chunk);
        void onComplete(String fullText, double responseTimeSec);
        void onError(String errorMessage);
    }

    public static GeminiClient getInstance() {
        if (sInstance == null) {
            synchronized (GeminiClient.class) {
                if (sInstance == null) {
                    sInstance = new GeminiClient();
                }
            }
        }
        return sInstance;
    }

    private GeminiClient() {
        mExecutors = AppExecutors.getInstance();
    }

    /**
     * Generates a single plain-language explanation for an earthquake event.
     */
    public void generateExplanation(String prompt, ExplanationCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mMainHandler.post(() -> callback.onError("AI key missing. Please check local.properties."));
            return;
        }

        mExecutors.networkIO().execute(() -> {
            long startTime = System.currentTimeMillis();
            long deadline = startTime + TOTAL_DEADLINE_MS;

            for (String model : MODELS) {
                if (System.currentTimeMillis() >= deadline) break;

                // Try with minimal thinking config first
                String result = executeSingleRequest(model, prompt, apiKey, deadline, true);
                if (result == null && System.currentTimeMillis() < deadline) {
                    // Retry once without thinkingConfig on HTTP 400
                    result = executeSingleRequest(model, prompt, apiKey, deadline, false);
                }

                if (result != null && !result.trim().isEmpty()) {
                    String clean = cleanMarkdown(result.trim());
                    mMainHandler.post(() -> callback.onSuccess(clean));
                    return;
                }
            }

            mMainHandler.post(() -> callback.onError("The AI service is busy. Tap to try again."));
        });
    }

    private String executeSingleRequest(String model, String prompt, String apiKey, long deadline, boolean withThinking) {
        HttpURLConnection conn = null;
        try {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return null;

            URL url = new URL(BASE_URL + model + ":generateContent");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setConnectTimeout((int) Math.min(5000, remaining));
            conn.setReadTimeout((int) Math.min(10000, remaining));
            conn.setDoOutput(true);

            JSONObject payload = buildPayload(prompt, 400, withThinking);
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return extractTextFromResponse(sb.toString());
                }
            } else if (responseCode == 400 && withThinking) {
                // Retry this same model without thinkingConfig
                return null;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Streams conversational responses for the Ask AI chat using Server-Sent Events (SSE).
     */
    public void streamChat(List<ChatMessage> conversationHistory, StreamCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mMainHandler.post(() -> callback.onError("AI key missing. Please check local.properties."));
            return;
        }

        mExecutors.networkIO().execute(() -> {
            long startTime = System.currentTimeMillis();
            long deadline = startTime + TOTAL_DEADLINE_MS;

            for (String model : MODELS) {
                if (System.currentTimeMillis() >= deadline) break;

                boolean success = executeStreamRequest(model, conversationHistory, apiKey, startTime, deadline, callback, true);
                if (!success && System.currentTimeMillis() < deadline) {
                    success = executeStreamRequest(model, conversationHistory, apiKey, startTime, deadline, callback, false);
                }

                if (success) {
                    return;
                }
            }

            mMainHandler.post(() -> callback.onError("The AI service is busy. Tap to try again."));
        });
    }

    private boolean executeStreamRequest(String model, List<ChatMessage> history, String apiKey,
                                         long startTime, long deadline, StreamCallback callback, boolean withThinking) {
        HttpURLConnection conn = null;
        StringBuilder accumulated = new StringBuilder();

        try {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;

            URL url = new URL(BASE_URL + model + ":streamGenerateContent?alt=sse");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setConnectTimeout((int) Math.min(5000, remaining));
            conn.setReadTimeout((int) Math.min(10000, remaining));
            conn.setDoOutput(true);

            JSONObject payload = buildChatPayload(history, 600, withThinking);
            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (System.currentTimeMillis() >= deadline) {
                            accumulated.append("\n(stopped after 30 seconds)");
                            break;
                        }

                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6).trim();
                            if (!jsonStr.isEmpty()) {
                                String chunk = parseChunkText(jsonStr);
                                if (!chunk.isEmpty()) {
                                    String clean = cleanMarkdown(chunk);
                                    accumulated.append(clean);
                                    mMainHandler.post(() -> callback.onChunk(clean));
                                }
                            }
                        }
                    }

                    double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
                    String fullText = accumulated.toString().trim();
                    mMainHandler.post(() -> callback.onComplete(fullText, elapsedSec));
                    return true;
                }
            } else {
                try (InputStream es = conn.getErrorStream();
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                    StringBuilder errSb = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errSb.append(errLine);
                    }
                    android.util.Log.e("GeminiClient", "Stream error " + responseCode + ": " + errSb.toString());
                } catch (Exception ignored) {
                }
                if (responseCode == 400 && withThinking) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    private JSONObject buildPayload(String prompt, int maxTokens, boolean withThinking) throws Exception {
        JSONObject payload = new JSONObject();

        JSONArray contents = new JSONArray();
        JSONObject userTurn = new JSONObject();
        userTurn.put("role", "user");

        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        parts.put(part);
        userTurn.put("parts", parts);
        contents.put(userTurn);

        payload.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.5);
        genConfig.put("maxOutputTokens", maxTokens);
        if (withThinking) {
            JSONObject thinking = new JSONObject();
            thinking.put("thinkingLevel", "minimal");
            genConfig.put("thinkingConfig", thinking);
        }
        payload.put("generationConfig", genConfig);

        return payload;
    }

    private JSONObject buildChatPayload(List<ChatMessage> history, int maxTokens, boolean withThinking) throws Exception {
        JSONObject payload = new JSONObject();
        JSONArray contents = new JSONArray();

        String systemPrompt = "You are QuakeWatch Assistant. Answer questions about earthquakes, preparedness and safety in plain text with no markdown symbols, in under 100 words unless asked for more. Be calm and practical. Never claim to predict earthquakes. For emergencies tell the user to call 112 and follow local authorities. If the question is unrelated to earthquakes or safety, politely steer back to the topic.\n\n";

        // Filter out empty messages (such as the assistant placeholder currently being streamed)
        List<ChatMessage> validTurns = new ArrayList<>();
        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg != null && msg.getText() != null && !msg.getText().trim().isEmpty()) {
                    validTurns.add(msg);
                }
            }
        }

        // Keep last 8 valid turns as conversational context
        int startIdx = Math.max(0, validTurns.size() - 8);

        for (int i = startIdx; i < validTurns.size(); i++) {
            ChatMessage msg = validTurns.get(i);
            JSONObject turn = new JSONObject();
            turn.put("role", msg.isUser() ? "user" : "model");

            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            String text = msg.getText();
            if (i == startIdx && msg.isUser()) {
                text = systemPrompt + text;
            }
            part.put("text", text);
            parts.put(part);
            turn.put("parts", parts);

            contents.put(turn);
        }

        payload.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.5);
        genConfig.put("maxOutputTokens", maxTokens);
        if (withThinking) {
            JSONObject thinking = new JSONObject();
            thinking.put("thinkingLevel", "minimal");
            genConfig.put("thinkingConfig", thinking);
        }
        payload.put("generationConfig", genConfig);

        return payload;
    }

    private String extractTextFromResponse(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (!part.optBoolean("thought", false)) {
                                sb.append(part.optString("text", ""));
                            }
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String parseChunkText(String sseJson) {
        try {
            JSONObject root = new JSONObject(sseJson);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject cand = candidates.getJSONObject(0);
                JSONObject content = cand.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (!part.optBoolean("thought", false)) {
                                sb.append(part.optString("text", ""));
                            }
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Cleans markdown formatting (asterisks, hashtags, backticks) to enforce clean plain text.
     */
    public static String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replace("**", "")
                .replace("*", "")
                .replace("### ", "")
                .replace("## ", "")
                .replace("# ", "")
                .replace("`", "")
                .trim();
    }
}
