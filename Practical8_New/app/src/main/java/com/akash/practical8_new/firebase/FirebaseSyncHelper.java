package com.akash.practical8_new.firebase;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.akash.practical8_new.model.LocationModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles real-time synchronization of location coordinates using Firebase Cloud Firestore.
 * Conforms to authorized security rules under users/{uid}.
 */
public class FirebaseSyncHelper {
    private static final String TAG = "FirebaseSyncHelper";
    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private ListenerRegistration realtimeListenerRegistration;
    private RealtimeUpdateCallback pendingCallback;

    public interface SyncCallback {
        void onSuccess(String documentId);
        void onError(Exception e);
    }

    public interface RealtimeUpdateCallback {
        void onDataUpdated(List<LocationModel> locations);
        void onError(Exception e);
    }

    public FirebaseSyncHelper(Context context) {
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance("Practical8Firebase");
        } catch (IllegalStateException e) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:64073902603:android:b17abba583119bb1c6f50e")
                    .setApiKey("AIzaSyCwPoEv7b2eBxkjtkNtULrRjNfrL16W3vc")
                    .setProjectId("quakewatch-2fe83")
                    .setStorageBucket("quakewatch-2fe83.firebasestorage.app")
                    .build();
            app = FirebaseApp.initializeApp(context.getApplicationContext(), options, "Practical8Firebase");
        }
        this.firestore = FirebaseFirestore.getInstance(app);
        this.auth = FirebaseAuth.getInstance(app);

        ensureAuthenticatedSession();
    }

    private void ensureAuthenticatedSession() {
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnSuccessListener(authResult -> {
                Log.d(TAG, "Anonymous auth successful: " + authResult.getUser().getUid());
                if (pendingCallback != null) {
                    startListeningInternal(pendingCallback);
                }
            }).addOnFailureListener(e -> {
                Log.w(TAG, "Anonymous auth disabled: " + e.getMessage() + ". Attempting student email auth...");
                auth.signInWithEmailAndPassword("student@practical8.com", "College123456")
                        .addOnSuccessListener(result -> {
                            Log.d(TAG, "Student email auth successful: " + result.getUser().getUid());
                            if (pendingCallback != null) {
                                startListeningInternal(pendingCallback);
                            }
                        })
                        .addOnFailureListener(e2 -> {
                            auth.createUserWithEmailAndPassword("student@practical8.com", "College123456")
                                    .addOnSuccessListener(createResult -> {
                                        Log.d(TAG, "Created student user: " + createResult.getUser().getUid());
                                        if (pendingCallback != null) {
                                            startListeningInternal(pendingCallback);
                                        }
                                    })
                                    .addOnFailureListener(e3 -> Log.e(TAG, "Auth failed: " + e3.getMessage()));
                        });
            });
        }
    }

    /**
     * Uploads the latest GPS location to the user's Firestore cloud document.
     */
    public void syncLocation(LocationModel location, SyncCallback callback) {
        if (location == null) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onError(new Exception("Waiting for Firebase authentication..."));
            return;
        }

        String uid = user.getUid();
        DocumentReference userDoc = firestore.collection(COLLECTION_USERS).document(uid);

        Map<String, Object> update = new HashMap<>();
        update.put("displayName", "Practical 8 Student");
        update.put("lastActive", System.currentTimeMillis());
        update.put("latestLocation", location.toMap());
        update.put("locationHistory", FieldValue.arrayUnion(location.toMap()));

        userDoc.set(update, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location successfully synced to Firebase Firestore!");
                    if (callback != null) callback.onSuccess(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore sync error: " + e.getMessage());
                    if (callback != null) callback.onError(e);
                });
    }

    /**
     * Listens in real-time for changes to the cloud location data.
     */
    public void startRealtimeListener(RealtimeUpdateCallback callback) {
        this.pendingCallback = callback;
        if (auth.getCurrentUser() != null) {
            startListeningInternal(callback);
        }
    }

    @SuppressWarnings("unchecked")
    private void startListeningInternal(RealtimeUpdateCallback callback) {
        stopRealtimeListener();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        DocumentReference userDoc = firestore.collection(COLLECTION_USERS).document(user.getUid());
        realtimeListenerRegistration = userDoc.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed: " + e.getMessage());
                    if (callback != null) callback.onError(e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    List<LocationModel> list = new ArrayList<>();
                    List<Map<String, Object>> history = (List<Map<String, Object>>) snapshot.get("locationHistory");
                    if (history != null) {
                        for (int i = history.size() - 1; i >= 0; i--) {
                            Map<String, Object> map = history.get(i);
                            LocationModel m = new LocationModel();
                            m.setId((String) map.get("id"));
                            Object lat = map.get("latitude");
                            if (lat instanceof Number) m.setLatitude(((Number) lat).doubleValue());
                            Object lng = map.get("longitude");
                            if (lng instanceof Number) m.setLongitude(((Number) lng).doubleValue());
                            Object acc = map.get("accuracy");
                            if (acc instanceof Number) m.setAccuracy(((Number) acc).floatValue());
                            m.setAddress((String) map.get("address"));
                            Object ts = map.get("timestamp");
                            if (ts instanceof Number) m.setTimestamp(((Number) ts).longValue());
                            m.setDeviceName((String) map.get("deviceName"));
                            list.add(m);
                        }
                    }

                    if (list.isEmpty() && snapshot.contains("latestLocation")) {
                        Map<String, Object> latest = (Map<String, Object>) snapshot.get("latestLocation");
                        if (latest != null) {
                            LocationModel m = new LocationModel();
                            m.setId((String) latest.get("id"));
                            Object lat = latest.get("latitude");
                            if (lat instanceof Number) m.setLatitude(((Number) lat).doubleValue());
                            Object lng = latest.get("longitude");
                            if (lng instanceof Number) m.setLongitude(((Number) lng).doubleValue());
                            m.setAddress((String) latest.get("address"));
                            list.add(m);
                        }
                    }

                    if (callback != null) {
                        callback.onDataUpdated(list);
                    }
                }
            }
        });
    }

    /**
     * Detaches the real-time listener to prevent memory leaks.
     */
    public void stopRealtimeListener() {
        if (realtimeListenerRegistration != null) {
            realtimeListenerRegistration.remove();
            realtimeListenerRegistration = null;
        }
    }
}
