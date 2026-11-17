package com.akash.quakewatch.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.akash.quakewatch.data.model.ChatMessage;
import com.akash.quakewatch.data.model.ChatSession;
import com.akash.quakewatch.data.model.Quake;

import java.util.ArrayList;
import java.util.List;

/**
 * QuakeDbHelper manages local SQLite persistence for earthquake records, AI explanations,
 * and conversational chat history (sessions and messages).
 * It provides transaction-safe batch insertions, index-optimized queries,
 * and maintains a 30-day offline cache for uninterrupted operation without network connectivity.
 */
public class QuakeDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "quakewatch.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_QUAKES = "quakes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_PLACE = "place";
    public static final String COLUMN_MAGNITUDE = "magnitude";
    public static final String COLUMN_MAG_TYPE = "mag_type";
    public static final String COLUMN_DEPTH_KM = "depth_km";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_TIME_MS = "time_ms";

    public static final String TABLE_AI_CACHE = "ai_cache";
    public static final String COLUMN_AI_QUAKE_ID = "quake_id";
    public static final String COLUMN_AI_EXPLANATION = "explanation";
    public static final String COLUMN_AI_CREATED_MS = "created_ms";

    public static final String TABLE_CHAT_SESSIONS = "chat_sessions";
    public static final String COLUMN_SESSION_ID = "id";
    public static final String COLUMN_SESSION_TITLE = "title";
    public static final String COLUMN_SESSION_CREATED_MS = "created_ms";
    public static final String COLUMN_SESSION_UPDATED_MS = "updated_ms";

    public static final String TABLE_CHAT_MESSAGES = "chat_messages";
    public static final String COLUMN_MSG_ID = "id";
    public static final String COLUMN_MSG_SESSION_ID = "session_id";
    public static final String COLUMN_MSG_ROLE = "role";
    public static final String COLUMN_MSG_TEXT = "text";
    public static final String COLUMN_MSG_LATENCY = "latency_sec";
    public static final String COLUMN_MSG_CREATED_MS = "created_ms";

    private static volatile QuakeDbHelper sInstance;

    public static QuakeDbHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (QuakeDbHelper.class) {
                if (sInstance == null) {
                    sInstance = new QuakeDbHelper(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private QuakeDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUAKES + " ("
                + COLUMN_ID + " TEXT PRIMARY KEY, "
                + COLUMN_PLACE + " TEXT NOT NULL, "
                + COLUMN_MAGNITUDE + " REAL NOT NULL, "
                + COLUMN_MAG_TYPE + " TEXT, "
                + COLUMN_DEPTH_KM + " REAL NOT NULL, "
                + COLUMN_LATITUDE + " REAL NOT NULL, "
                + COLUMN_LONGITUDE + " REAL NOT NULL, "
                + COLUMN_TIME_MS + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE INDEX idx_quakes_time ON " + TABLE_QUAKES + "(" + COLUMN_TIME_MS + " DESC);");

        db.execSQL("CREATE TABLE " + TABLE_AI_CACHE + " ("
                + COLUMN_AI_QUAKE_ID + " TEXT PRIMARY KEY, "
                + COLUMN_AI_EXPLANATION + " TEXT NOT NULL, "
                + COLUMN_AI_CREATED_MS + " INTEGER NOT NULL"
                + ");");

        createChatTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createChatTables(db);
        } else {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUAKES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_AI_CACHE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_MESSAGES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_SESSIONS);
            onCreate(db);
        }
    }

    private void createChatTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CHAT_SESSIONS + " ("
                + COLUMN_SESSION_ID + " TEXT PRIMARY KEY, "
                + COLUMN_SESSION_TITLE + " TEXT NOT NULL, "
                + COLUMN_SESSION_CREATED_MS + " INTEGER NOT NULL, "
                + COLUMN_SESSION_UPDATED_MS + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CHAT_MESSAGES + " ("
                + COLUMN_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_MSG_SESSION_ID + " TEXT NOT NULL, "
                + COLUMN_MSG_ROLE + " TEXT NOT NULL, "
                + COLUMN_MSG_TEXT + " TEXT NOT NULL, "
                + COLUMN_MSG_LATENCY + " REAL NOT NULL, "
                + COLUMN_MSG_CREATED_MS + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_msg_session ON " + TABLE_CHAT_MESSAGES
                + "(" + COLUMN_MSG_SESSION_ID + ");");
    }

    /**
     * Inserts or replaces a list of earthquakes inside a single SQLite transaction.
     */
    public void saveQuakes(List<Quake> quakes) {
        if (quakes == null || quakes.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            for (Quake quake : quakes) {
                values.clear();
                values.put(COLUMN_ID, quake.getId());
                values.put(COLUMN_PLACE, quake.getPlace());
                values.put(COLUMN_MAGNITUDE, quake.getMagnitude());
                values.put(COLUMN_MAG_TYPE, quake.getMagType());
                values.put(COLUMN_DEPTH_KM, quake.getDepthKm());
                values.put(COLUMN_LATITUDE, quake.getLatitude());
                values.put(COLUMN_LONGITUDE, quake.getLongitude());
                values.put(COLUMN_TIME_MS, quake.getTimeMs());

                db.insertWithOnConflict(TABLE_QUAKES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Retrieves all cached earthquakes ordered by time descending (newest first).
     */
    public List<Quake> getAllQuakes() {
        List<Quake> quakes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_QUAKES + " ORDER BY " + COLUMN_TIME_MS + " DESC";

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int placeIdx = cursor.getColumnIndexOrThrow(COLUMN_PLACE);
                int magIdx = cursor.getColumnIndexOrThrow(COLUMN_MAGNITUDE);
                int magTypeIdx = cursor.getColumnIndexOrThrow(COLUMN_MAG_TYPE);
                int depthIdx = cursor.getColumnIndexOrThrow(COLUMN_DEPTH_KM);
                int latIdx = cursor.getColumnIndexOrThrow(COLUMN_LATITUDE);
                int lonIdx = cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIME_MS);

                do {
                    Quake quake = new Quake(
                            cursor.getString(idIdx),
                            cursor.getString(placeIdx),
                            cursor.getDouble(magIdx),
                            cursor.getString(magTypeIdx),
                            cursor.getDouble(depthIdx),
                            cursor.getDouble(latIdx),
                            cursor.getDouble(lonIdx),
                            cursor.getLong(timeIdx)
                    );
                    quakes.add(quake);
                } while (cursor.moveToNext());
            }
        }
        return quakes;
    }

    /**
     * Retrieves a single earthquake record by its unique USGS ID.
     */
    public Quake getQuakeById(String id) {
        if (id == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_QUAKES + " WHERE " + COLUMN_ID + " = ? LIMIT 1";
        try (Cursor cursor = db.rawQuery(query, new String[]{id})) {
            if (cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int placeIdx = cursor.getColumnIndexOrThrow(COLUMN_PLACE);
                int magIdx = cursor.getColumnIndexOrThrow(COLUMN_MAGNITUDE);
                int magTypeIdx = cursor.getColumnIndexOrThrow(COLUMN_MAG_TYPE);
                int depthIdx = cursor.getColumnIndexOrThrow(COLUMN_DEPTH_KM);
                int latIdx = cursor.getColumnIndexOrThrow(COLUMN_LATITUDE);
                int lonIdx = cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIME_MS);

                return new Quake(
                        cursor.getString(idIdx),
                        cursor.getString(placeIdx),
                        cursor.getDouble(magIdx),
                        cursor.getString(magTypeIdx),
                        cursor.getDouble(depthIdx),
                        cursor.getDouble(latIdx),
                        cursor.getDouble(lonIdx),
                        cursor.getLong(timeIdx)
                );
            }
        }
        return null;
    }

    /**
     * Deletes earthquakes older than the given timestamp threshold.
     */
    public void deleteOlderThan(long thresholdMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_QUAKES, COLUMN_TIME_MS + " < ?", new String[]{String.valueOf(thresholdMs)});
    }

    /**
     * Clears all cached earthquakes (used when switching geographic regions).
     */
    public void clearAllQuakes() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_QUAKES, null, null);
    }

    /**
     * Retrieves a cached Gemini explanation for an earthquake, if previously generated.
     */
    public String getAiExplanation(String quakeId) {
        if (quakeId == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT " + COLUMN_AI_EXPLANATION + " FROM " + TABLE_AI_CACHE
                + " WHERE " + COLUMN_AI_QUAKE_ID + " = ? LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{quakeId})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    /**
     * Saves a Gemini explanation into SQLite so subsequent viewings load instantly offline.
     */
    public void saveAiExplanation(String quakeId, String explanation) {
        if (quakeId == null || explanation == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AI_QUAKE_ID, quakeId);
        values.put(COLUMN_AI_EXPLANATION, explanation);
        values.put(COLUMN_AI_CREATED_MS, System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_AI_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Creates a new chat session or updates its title and timestamp.
     */
    public void createOrUpdateSession(String sessionId, String title, long updatedMs) {
        if (sessionId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SESSION_ID, sessionId);
        values.put(COLUMN_SESSION_TITLE, title != null ? title : "Conversation");
        values.put(COLUMN_SESSION_UPDATED_MS, updatedMs);

        // Check if session exists to preserve original created_ms
        String query = "SELECT " + COLUMN_SESSION_CREATED_MS + " FROM " + TABLE_CHAT_SESSIONS
                + " WHERE " + COLUMN_SESSION_ID + " = ? LIMIT 1";
        long createdMs = updatedMs;
        try (Cursor cursor = db.rawQuery(query, new String[]{sessionId})) {
            if (cursor.moveToFirst()) {
                createdMs = cursor.getLong(0);
            }
        }
        values.put(COLUMN_SESSION_CREATED_MS, createdMs);

        db.insertWithOnConflict(TABLE_CHAT_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Saves a single completed chat message to the persistent store.
     */
    public void saveChatMessage(String sessionId, ChatMessage msg) {
        if (sessionId == null || msg == null || msg.getText() == null || msg.getText().trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MSG_SESSION_ID, sessionId);
        values.put(COLUMN_MSG_ROLE, msg.getRole());
        values.put(COLUMN_MSG_TEXT, msg.getText());
        values.put(COLUMN_MSG_LATENCY, msg.getResponseTimeSec());
        values.put(COLUMN_MSG_CREATED_MS, msg.getTimestampMs());

        db.insert(TABLE_CHAT_MESSAGES, null, values);

        // Update session's updated_ms
        ContentValues sessionUpdate = new ContentValues();
        sessionUpdate.put(COLUMN_SESSION_UPDATED_MS, msg.getTimestampMs());
        db.update(TABLE_CHAT_SESSIONS, sessionUpdate, COLUMN_SESSION_ID + " = ?", new String[]{sessionId});
    }

    /**
     * Retrieves all chat sessions ordered by most recently updated first.
     */
    public List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT s." + COLUMN_SESSION_ID + ", s." + COLUMN_SESSION_TITLE + ", "
                + "s." + COLUMN_SESSION_CREATED_MS + ", s." + COLUMN_SESSION_UPDATED_MS + ", "
                + "(SELECT COUNT(*) FROM " + TABLE_CHAT_MESSAGES + " m WHERE m." + COLUMN_MSG_SESSION_ID + " = s." + COLUMN_SESSION_ID + ") as msg_count "
                + "FROM " + TABLE_CHAT_SESSIONS + " s "
                + "ORDER BY s." + COLUMN_SESSION_UPDATED_MS + " DESC";

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID);
                int titleIdx = cursor.getColumnIndexOrThrow(COLUMN_SESSION_TITLE);
                int createdIdx = cursor.getColumnIndexOrThrow(COLUMN_SESSION_CREATED_MS);
                int updatedIdx = cursor.getColumnIndexOrThrow(COLUMN_SESSION_UPDATED_MS);
                int countIdx = cursor.getColumnIndexOrThrow("msg_count");

                do {
                    ChatSession session = new ChatSession(
                            cursor.getString(idIdx),
                            cursor.getString(titleIdx),
                            cursor.getLong(createdIdx),
                            cursor.getLong(updatedIdx),
                            cursor.getInt(countIdx)
                    );
                    sessions.add(session);
                } while (cursor.moveToNext());
            }
        }
        return sessions;
    }

    /**
     * Retrieves all messages for a specific chat session in chronological order.
     */
    public List<ChatMessage> getMessagesForSession(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        if (sessionId == null) return messages;

        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT " + COLUMN_MSG_ROLE + ", " + COLUMN_MSG_TEXT + ", "
                + COLUMN_MSG_LATENCY + " FROM " + TABLE_CHAT_MESSAGES
                + " WHERE " + COLUMN_MSG_SESSION_ID + " = ? "
                + "ORDER BY " + COLUMN_MSG_ID + " ASC";

        try (Cursor cursor = db.rawQuery(query, new String[]{sessionId})) {
            if (cursor.moveToFirst()) {
                int roleIdx = cursor.getColumnIndexOrThrow(COLUMN_MSG_ROLE);
                int textIdx = cursor.getColumnIndexOrThrow(COLUMN_MSG_TEXT);
                int latencyIdx = cursor.getColumnIndexOrThrow(COLUMN_MSG_LATENCY);

                do {
                    ChatMessage msg = new ChatMessage(
                            cursor.getString(roleIdx),
                            cursor.getString(textIdx),
                            cursor.getDouble(latencyIdx),
                            false
                    );
                    messages.add(msg);
                } while (cursor.moveToNext());
            }
        }
        return messages;
    }

    /**
     * Deletes a chat session and all its associated messages.
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CHAT_MESSAGES, COLUMN_MSG_SESSION_ID + " = ?", new String[]{sessionId});
        db.delete(TABLE_CHAT_SESSIONS, COLUMN_SESSION_ID + " = ?", new String[]{sessionId});
    }
}
