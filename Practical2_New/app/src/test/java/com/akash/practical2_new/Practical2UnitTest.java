package com.akash.practical2_new;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.akash.practical2_new.data.model.ApiItem;
import com.akash.practical2_new.data.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests verifying data modeling, JSON deserialization,
 * and QA message handling for Practical No. 02.
 */
public class Practical2UnitTest {

    @Test
    public void testApiItemModel() {
        ApiItem item = new ApiItem("101", "REST Architecture", "REST relies on stateless communication", "Post #101");
        assertEquals("101", item.getId());
        assertEquals("REST Architecture", item.getTitle());
        assertEquals("REST relies on stateless communication", item.getBody());
        assertEquals("Post #101", item.getCategory());
    }

    @Test
    public void testChatMessageUserAndAiRoles() {
        ChatMessage userMsg = new ChatMessage("What is JSON?", ChatMessage.Sender.USER, 1000L, 0L);
        assertTrue(userMsg.isUser());
        assertEquals(ChatMessage.Sender.USER, userMsg.getSender());
        assertEquals("What is JSON?", userMsg.getText());

        ChatMessage aiMsg = new ChatMessage("JSON stands for JavaScript Object Notation.", ChatMessage.Sender.AI, 2000L, 850L);
        assertFalse(aiMsg.isUser());
        assertEquals(ChatMessage.Sender.AI, aiMsg.getSender());
        assertEquals(850L, aiMsg.getLatencyMs());
    }

    @Test
    public void testJsonParsing() throws Exception {
        String jsonArrayString = "[{\"userId\": 1, \"id\": 1, \"title\": \"Test Title\", \"body\": \"Test Body\"}]";
        JSONArray array = new JSONArray(jsonArrayString);
        assertEquals(1, array.length());

        JSONObject obj = array.getJSONObject(0);
        assertEquals(1, obj.getInt("id"));
        assertEquals("Test Title", obj.getString("title"));
        assertEquals("Test Body", obj.getString("body"));
    }

    @Test
    public void testEnglishPostsObjectParsing() throws Exception {
        String jsonObjectString = "{\"posts\": [{\"id\": 10, \"title\": \"English Post\", \"body\": \"Article content\", \"views\": 300}]}";
        JSONObject root = new JSONObject(jsonObjectString);
        assertTrue(root.has("posts"));
        JSONArray posts = root.getJSONArray("posts");
        assertEquals(1, posts.length());
        JSONObject post = posts.getJSONObject(0);
        assertEquals(10, post.getInt("id"));
        assertEquals("English Post", post.getString("title"));
    }
}
