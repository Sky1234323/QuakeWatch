package com.akash.quakewatch.data;

import com.akash.quakewatch.util.AppExecutors;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * AuthRepository handles all user authentication operations with Firebase Auth
 * and maintains the user profile document in Cloud Firestore.
 * It provides callbacks on the main thread for login, registration, and password reset.
 */
public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private static volatile AuthRepository sInstance;
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore mFirestore;
    private final AppExecutors mExecutors;

    private AuthRepository() {
        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        mExecutors = AppExecutors.getInstance();
    }

    public static AuthRepository getInstance() {
        if (sInstance == null) {
            synchronized (AuthRepository.class) {
                if (sInstance == null) {
                    sInstance = new AuthRepository();
                }
            }
        }
        return sInstance;
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public boolean isSignedIn() {
        return mAuth.getCurrentUser() != null;
    }

    public void signOut() {
        mAuth.signOut();
    }

    /**
     * Signs in an existing user with email and password.
     */
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        mAuth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    mExecutors.runOnMain(() -> callback.onSuccess(user));
                })
                .addOnFailureListener(e -> {
                    String message = getFriendlyErrorMessage(e);
                    mExecutors.runOnMain(() -> callback.onError(message));
                });
    }

    /**
     * Registers a new account, updates display name, and writes users/{uid} in Firestore.
     */
    public void registerWithEmail(String name, String email, String password, AuthCallback callback) {
        final String cleanName = name.trim();
        final String cleanEmail = email.trim();

        mAuth.createUserWithEmailAndPassword(cleanEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        mExecutors.runOnMain(() -> callback.onError("Registration failed. Please try again."));
                        return;
                    }

                    // Update profile display name
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build();

                    user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                        // Write users/{uid} document to Firestore
                        saveUserDocument(user.getUid(), cleanName, cleanEmail);
                        mExecutors.runOnMain(() -> callback.onSuccess(user));
                    });
                })
                .addOnFailureListener(e -> {
                    String message = getFriendlyErrorMessage(e);
                    mExecutors.runOnMain(() -> callback.onError(message));
                });
    }

    /**
     * Completes Firebase sign in using a Google ID token from Credential Manager.
     */
    public void signInWithGoogleToken(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        String name = user.getDisplayName() != null ? user.getDisplayName() : "User";
                        String email = user.getEmail() != null ? user.getEmail() : "";
                        saveUserDocument(user.getUid(), name, email);
                        mExecutors.runOnMain(() -> callback.onSuccess(user));
                    } else {
                        mExecutors.runOnMain(() -> callback.onError("Google sign-in returned no user."));
                    }
                })
                .addOnFailureListener(e -> {
                    String message = getFriendlyErrorMessage(e);
                    mExecutors.runOnMain(() -> callback.onError(message));
                });
    }

    /**
     * Sends a password reset email via Firebase Auth.
     */
    public void sendPasswordReset(String email, ActionCallback callback) {
        mAuth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(unused -> mExecutors.runOnMain(callback::onSuccess))
                .addOnFailureListener(e -> {
                    String message = getFriendlyErrorMessage(e);
                    mExecutors.runOnMain(() -> callback.onError(message));
                });
    }

    /**
     * Writes or updates user profile in Cloud Firestore: users/{uid}.
     */
    private void saveUserDocument(String uid, String displayName, String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("displayName", displayName);
        data.put("email", email);
        data.put("createdAt", System.currentTimeMillis());

        mFirestore.collection("users")
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> {
                    // Non-blocking log, local user is still authenticated
                });
    }

    /**
     * Translates Firebase exceptions into readable, student-friendly messages.
     */
    private String getFriendlyErrorMessage(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Invalid email or password. Please check and try again.";
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account exists with this email address.";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Try signing in.";
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Please use at least 6 characters.";
        } else if (e instanceof FirebaseNetworkException) {
            return "Unable to connect. Please verify your internet connection.";
        }
        return e.getMessage() != null ? e.getMessage() : "An unexpected error occurred. Please try again.";
    }
}
