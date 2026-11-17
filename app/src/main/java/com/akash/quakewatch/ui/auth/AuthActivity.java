package com.akash.quakewatch.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.AuthRepository;
import com.akash.quakewatch.databinding.ActivityAuthBinding;
import com.akash.quakewatch.ui.main.MainActivity;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

/**
 * AuthActivity handles user sign-in and account creation.
 * It provides segmented mode switching, form validation, password reset,
 * and Google authentication powered by Android Credential Manager.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding mBinding;
    private AuthRepository mAuthRepository;
    private boolean mIsRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.authRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuthRepository = AuthRepository.getInstance();

        setupModeToggle();
        setupActionButtons();
    }

    private void setupModeToggle() {
        mBinding.toggleAuthMode.check(R.id.btn_mode_signin);
        mBinding.toggleAuthMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            setRegisterMode(checkedId == R.id.btn_mode_register);
        });
    }

    private void setRegisterMode(boolean isRegister) {
        mIsRegisterMode = isRegister;
        clearErrors();

        if (isRegister) {
            mBinding.tvAuthSubtitle.setText("Create an account to join the community and report felt quakes");
            mBinding.tilName.setVisibility(View.VISIBLE);
            mBinding.tilConfirmPassword.setVisibility(View.VISIBLE);
            mBinding.btnForgotPassword.setVisibility(View.GONE);
            mBinding.btnPrimaryAction.setText("Create account");
        } else {
            mBinding.tvAuthSubtitle.setText("Sign in to save reports and receive alert updates");
            mBinding.tilName.setVisibility(View.GONE);
            mBinding.tilConfirmPassword.setVisibility(View.GONE);
            mBinding.btnForgotPassword.setVisibility(View.VISIBLE);
            mBinding.btnPrimaryAction.setText("Sign in");
        }
    }

    private void setupActionButtons() {
        mBinding.btnPrimaryAction.setOnClickListener(v -> submitForm());
        mBinding.btnForgotPassword.setOnClickListener(v -> handleForgotPassword());
        mBinding.btnGoogleSignin.setOnClickListener(v -> initiateGoogleSignIn());
    }

    private void submitForm() {
        clearErrors();

        String email = mBinding.etEmail.getText() != null ? mBinding.etEmail.getText().toString().trim() : "";
        String password = mBinding.etPassword.getText() != null ? mBinding.etPassword.getText().toString().trim() : "";

        boolean hasError = false;

        // Name validation (create mode)
        String name = "";
        if (mIsRegisterMode) {
            name = mBinding.etName.getText() != null ? mBinding.etName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                mBinding.tilName.setError("Please enter your name");
                hasError = true;
            }
        }

        // Email validation
        if (TextUtils.isEmpty(email)) {
            mBinding.tilEmail.setError("Email is required");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mBinding.tilEmail.setError("Enter a valid email address");
            hasError = true;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            mBinding.tilPassword.setError("Password is required");
            hasError = true;
        } else if (password.length() < 6) {
            mBinding.tilPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        // Confirm password validation (create mode)
        if (mIsRegisterMode) {
            String confirmPassword = mBinding.etConfirmPassword.getText() != null
                    ? mBinding.etConfirmPassword.getText().toString().trim() : "";
            if (!password.equals(confirmPassword)) {
                mBinding.tilConfirmPassword.setError("Passwords do not match");
                hasError = true;
            }
        }

        if (hasError) {
            return;
        }

        setLoading(true);

        if (mIsRegisterMode) {
            mAuthRepository.registerWithEmail(name, email, password, new AuthRepository.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    setLoading(false);
                    navigateToMain();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    showBannerError(message);
                }
            });
        } else {
            mAuthRepository.signInWithEmail(email, password, new AuthRepository.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    setLoading(false);
                    navigateToMain();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    showBannerError(message);
                }
            });
        }
    }

    private void handleForgotPassword() {
        clearErrors();
        String email = mBinding.etEmail.getText() != null ? mBinding.etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mBinding.tilEmail.setError("Enter your email address to receive a reset link");
            mBinding.etEmail.requestFocus();
            return;
        }

        setLoading(true);
        mAuthRepository.sendPasswordReset(email, new AuthRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Snackbar.make(mBinding.getRoot(), "Password reset email sent to " + email, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showBannerError(message);
            }
        });
    }

    private void initiateGoogleSignIn() {
        clearErrors();

        String webClientId;
        try {
            webClientId = getString(R.string.default_web_client_id);
        } catch (Exception e) {
            webClientId = "";
        }

        if (TextUtils.isEmpty(webClientId) || webClientId.contains("mock")) {
            showBannerError("Google sign-in requires real google-services.json from your Firebase project.");
            return;
        }

        setLoading(true);

        GetSignInWithGoogleOption googleIdOption = new GetSignInWithGoogleOption.Builder(webClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(this);

        credentialManager.getCredentialAsync(
                this,
                request,
                null,
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                            try {
                                GoogleIdTokenCredential tokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                                String idToken = tokenCredential.getIdToken();
                                completeGoogleLogin(idToken);
                            } catch (Exception e) {
                                setLoading(false);
                                showBannerError("Failed to parse Google sign-in response: " + e.getMessage());
                            }
                        } else {
                            setLoading(false);
                            showBannerError("Unexpected credential format received.");
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        setLoading(false);
                        if (!(e instanceof GetCredentialCancellationException)) {
                            showBannerError("Google sign-in error: " + e.getMessage());
                        }
                    }
                }
        );
    }

    private void completeGoogleLogin(String idToken) {
        mAuthRepository.signInWithGoogleToken(idToken, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                navigateToMain();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showBannerError(message);
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        mBinding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        mBinding.btnPrimaryAction.setEnabled(!loading);
        mBinding.btnGoogleSignin.setEnabled(!loading);
        mBinding.btnForgotPassword.setEnabled(!loading);
        mBinding.btnModeSignin.setEnabled(!loading);
        mBinding.btnModeRegister.setEnabled(!loading);
    }

    private void showBannerError(String message) {
        mBinding.tvErrorBanner.setText(message);
        mBinding.tvErrorBanner.setVisibility(View.VISIBLE);
    }

    private void clearErrors() {
        mBinding.tvErrorBanner.setVisibility(View.GONE);
        mBinding.tilName.setError(null);
        mBinding.tilEmail.setError(null);
        mBinding.tilPassword.setError(null);
        mBinding.tilConfirmPassword.setError(null);
    }
}
