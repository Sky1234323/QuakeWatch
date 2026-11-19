package com.akash.practical2_new.data.model;

/**
 * ChatMessage represents a single QA turn between the student
 * and the Gemini AI mobile assistant.
 */
public class ChatMessage {

    public enum Sender {
        USER,
        AI
    }

    private final String text;
    private final Sender sender;
    private final long timestamp;
    private final long latencyMs;

    public ChatMessage(String text, Sender sender, long timestamp, long latencyMs) {
        this.text = text;
        this.sender = sender;
        this.timestamp = timestamp;
        this.latencyMs = latencyMs;
    }

    public String getText() {
        return text;
    }

    public Sender getSender() {
        return sender;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isUser() {
        return sender == Sender.USER;
    }
}
