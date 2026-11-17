package com.akash.quakewatch.data;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * FeltRepository coordinates real-time community "I felt this" reports with Cloud Firestore.
 * It manages document creation and deletion under felt_reports/{quakeId}_{uid} ensuring
 * each user submits at most one report per earthquake event.
 */
public class FeltRepository {

    private static final String COLLECTION_FELT = "felt_reports";
    private static volatile FeltRepository sInstance;

    private final FirebaseFirestore mFirestore;
    private final FirebaseAuth mAuth;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface QuakeFeltListener {
        void onFeltChanged(int count, boolean userHasFelt);
    }

    public interface AllCountsListener {
        void onCountsUpdated(Map<String, Integer> counts);
    }

    public interface ToggleCallback {
        void onSuccess(boolean isNowFelt);
        void onError(String message);
    }

    public static FeltRepository getInstance() {
        if (sInstance == null) {
            synchronized (FeltRepository.class) {
                if (sInstance == null) {
                    sInstance = new FeltRepository();
                }
            }
        }
        return sInstance;
    }

    private FeltRepository() {
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    /**
     * Attaches a real-time snapshot listener for a specific earthquake.
     * Returns a ListenerRegistration that must be removed when the observing screen is destroyed.
     */
    public ListenerRegistration listenCount(String quakeId, QuakeFeltListener listener) {
        if (quakeId == null || listener == null) return null;

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        return mFirestore.collection(COLLECTION_FELT)
                .whereEqualTo("quakeId", quakeId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        mMainHandler.post(() -> listener.onFeltChanged(0, false));
                        return;
                    }

                    int count = 0;
                    boolean userHasFelt = false;

                    if (snapshots != null) {
                        count = snapshots.size();
                        if (currentUid != null) {
                            for (QueryDocumentSnapshot doc : snapshots) {
                                String uid = doc.getString("uid");
                                if (currentUid.equals(uid)) {
                                    userHasFelt = true;
                                    break;
                                }
                            }
                        }
                    }

                    int finalCount = count;
                    boolean finalUserHasFelt = userHasFelt;
                    mMainHandler.post(() -> listener.onFeltChanged(finalCount, finalUserHasFelt));
                });
    }

    /**
     * Attaches a global snapshot listener for the most recent 500 reports,
     * aggregating felt counts per earthquake ID for the Home list and Felt chip filter.
     */
    public ListenerRegistration listenAllCounts(AllCountsListener listener) {
        if (listener == null) return null;

        return mFirestore.collection(COLLECTION_FELT)
                .limit(500)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("FeltRepository", "Error listening to all felt counts: " + error.getMessage(), error);
                        return;
                    }
                    if (snapshots == null) return;

                    Map<String, Integer> countsMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String qId = doc.getString("quakeId");
                        if (qId != null && !qId.isEmpty()) {
                            int current = countsMap.getOrDefault(qId, 0);
                            countsMap.put(qId, current + 1);
                        }
                    }

                    android.util.Log.d("FeltRepository", "Updated felt counts map: " + countsMap);
                    mMainHandler.post(() -> listener.onCountsUpdated(countsMap));
                });
    }

    /**
     * Toggles the current user's felt status for the given earthquake ID.
     * If already reported, deletes the document; if not reported, creates the document.
     */
    public void toggleFelt(String quakeId, ToggleCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (callback != null) {
                callback.onError("Please sign in to submit a felt report.");
            }
            return;
        }

        String uid = currentUser.getUid();
        String reportDocId = quakeId + "_" + uid;
        DocumentReference docRef = mFirestore.collection(COLLECTION_FELT).document(reportDocId);

        docRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                // Remove existing report
                docRef.delete()
                        .addOnSuccessListener(aVoid -> {
                            if (callback != null) callback.onSuccess(false);
                        })
                        .addOnFailureListener(e -> {
                            if (callback != null) callback.onError(e.getMessage());
                        });
            } else {
                // Create new report
                Map<String, Object> data = new HashMap<>();
                data.put("quakeId", quakeId);
                data.put("uid", uid);
                data.put("userName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Anonymous");
                data.put("createdAt", System.currentTimeMillis());

                docRef.set(data)
                        .addOnSuccessListener(aVoid -> {
                            if (callback != null) callback.onSuccess(true);
                        })
                        .addOnFailureListener(e -> {
                            if (callback != null) callback.onError(e.getMessage());
                        });
            }
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onError(e.getMessage());
        });
    }
}
