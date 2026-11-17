package com.akash.quakewatch.data.model;

/**
 * ChatMessage represents a single turn in the Ask AI conversational assistant.
 * It tracks message role (user or model), textual content, response latency,
 * and whether the message is actively receiving streaming chunks.
 */
public class ChatMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_MODEL = "model";

    private final String role;
    private String text;
    private double responseTimeSec;
    private boolean isStreaming;
    private final long timestampMs;

    public ChatMessage(String role, String text, double responseTimeSec, boolean isStreaming) {
        this.role = role != null ? role : ROLE_USER;
        this.text = text != null ? text : "";
        this.responseTimeSec = responseTimeSec;
        this.isStreaming = isStreaming;
        this.timestampMs = System.currentTimeMillis();
    }

    public String getRole() {
        return role;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void appendText(String chunk) {
        if (chunk != null) {
            this.text += chunk;
        }
    }

    public double getResponseTimeSec() {
        return responseTimeSec;
    }

    public void setResponseTimeSec(double responseTimeSec) {
        this.responseTimeSec = responseTimeSec;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        this.isStreaming = streaming;
    }

    public long getTimestampMs() {
        return timestampMs;
    }
}
