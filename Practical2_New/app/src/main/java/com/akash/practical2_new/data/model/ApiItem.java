package com.akash.practical2_new.data.model;

/**
 * ApiItem models a record fetched from a REST API endpoint.
 * It holds both structured fields (for clean UI display) and
 * original metadata for viva presentation.
 */
public class ApiItem {
    private final String id;
    private final String title;
    private final String body;
    private final String category;

    public ApiItem(String id, String title, String body, String category) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getCategory() {
        return category;
    }
}
