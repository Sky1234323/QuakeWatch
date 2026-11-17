package com.akash.quakewatch.data.model;

/**
 * ChatSession represents a persistent conversation thread in the Ask AI assistant.
 * It contains a unique session identifier, an auto-generated title based on the first prompt,
 * creation and last active timestamps, and the number of messages exchanged.
 */
public class ChatSession {

    private final String id;
    private final String title;
    private final long createdMs;
    private final long updatedMs;
    private int messageCount;

    public ChatSession(String id, String title, long createdMs, long updatedMs, int messageCount) {
        this.id = id;
        this.title = title != null ? title : "Conversation";
        this.createdMs = createdMs;
        this.updatedMs = updatedMs;
        this.messageCount = messageCount;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getCreatedMs() {
        return createdMs;
    }

    public long getUpdatedMs() {
        return updatedMs;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
