package com.akash.quakewatch.ui.profile;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.akash.quakewatch.data.AuthRepository;
import com.akash.quakewatch.data.QuakeRepository;
import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.databinding.FragmentProfileBinding;
import com.akash.quakewatch.service.QuakeCheckWorker;
import com.akash.quakewatch.ui.auth.AuthActivity;
import com.akash.quakewatch.util.NotificationHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

/**
 * ProfileFragment manages user identity, regional seismic filters,
 * background notification polling thresholds, and account sign-out.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding mBinding;
    private SettingsStore mSettingsStore;

    private final ActivityResultLauncher<String> mNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableAlerts();
                } else {
                    if (mBinding != null) {
                        mBinding.switchAlerts.setChecked(false);
                    }
                    Toast.makeText(requireContext(), "Notification permission is required for earthquake alerts", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentProfileBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSettingsStore = SettingsStore.getInstance(requireContext());

        setupUserIdentity();
        setupRegionSetting();
        setupAlerts();
        setupSignOut();
    }

    private void setupUserIdentity() {
        FirebaseUser user = AuthRepository.getInstance().getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();

            if (name != null && !name.trim().isEmpty()) {
                mBinding.tvProfileName.setText(name);
                mBinding.tvAvatar.setText(getInitials(name));
            } else if (email != null && !email.trim().isEmpty()) {
                mBinding.tvProfileName.setText(email);
                mBinding.tvAvatar.setText(email.substring(0, 1).toUpperCase());
            }

            if (email != null && !email.trim().isEmpty()) {
                mBinding.tvProfileEmail.setText(email);
            }
        }
    }

    private void setupRegionSetting() {
        mBinding.tvSelectedRegion.setText(mSettingsStore.getRegion());

        mBinding.cardRegionSetting.setOnClickListener(v -> {
            String[] regions = {
                    SettingsStore.REGION_SOUTH_ASIA,
                    SettingsStore.REGION_WORLDWIDE,
                    SettingsStore.REGION_NEAR_ME
            };

            int checkedIndex = 0;
            String current = mSettingsStore.getRegion();
            for (int i = 0; i < regions.length; i++) {
                if (regions[i].equalsIgnoreCase(current)) {
                    checkedIndex = i;
                    break;
                }
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select Monitoring Region")
                    .setSingleChoiceItems(regions, checkedIndex, (dialog, which) -> {
                        String selected = regions[which];
                        mSettingsStore.setRegion(selected);
                        mBinding.tvSelectedRegion.setText(selected);
                        QuakeRepository.getInstance(requireContext()).onRegionChanged();
                        Toast.makeText(requireContext(), "Region updated: " + selected, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupAlerts() {
        boolean alertsOn = mSettingsStore.areAlertsEnabled();
        mBinding.switchAlerts.setChecked(alertsOn);
        mBinding.layoutAlertThreshold.setAlpha(alertsOn ? 1.0f : 0.5f);
        mBinding.sliderMagnitude.setEnabled(alertsOn);

        float currentMag = mSettingsStore.getAlertMagnitude();
        mBinding.sliderMagnitude.setValue(Math.max(4.0f, Math.min(7.0f, currentMag)));
        mBinding.tvAlertMagnitudeValue.setText(String.format(Locale.US, "M%.1f+", currentMag));

        mBinding.switchAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (NotificationHelper.hasPermission(requireContext())) {
                    enableAlerts();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    enableAlerts();
                }
            } else {
                disableAlerts();
            }
        });

        mBinding.sliderMagnitude.addOnChangeListener((slider, value, fromUser) -> {
            mSettingsStore.setAlertMagnitude(value);
            mBinding.tvAlertMagnitudeValue.setText(String.format(Locale.US, "M%.1f+", value));
        });

        mBinding.btnTestAlert.setOnClickListener(v -> {
            if (!NotificationHelper.hasPermission(requireContext()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                NotificationHelper.showTestAlert(requireContext());
                Toast.makeText(requireContext(), "Test alert posted to notification tray", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void enableAlerts() {
        mSettingsStore.setAlertsEnabled(true);
        if (mBinding != null) {
            mBinding.switchAlerts.setChecked(true);
            mBinding.layoutAlertThreshold.setAlpha(1.0f);
            mBinding.sliderMagnitude.setEnabled(true);
        }
        QuakeCheckWorker.schedule(requireContext());
        Toast.makeText(requireContext(), "Earthquake alerts enabled", Toast.LENGTH_SHORT).show();
    }

    private void disableAlerts() {
        mSettingsStore.setAlertsEnabled(false);
        if (mBinding != null) {
            mBinding.layoutAlertThreshold.setAlpha(0.5f);
            mBinding.sliderMagnitude.setEnabled(false);
        }
        QuakeCheckWorker.cancel(requireContext());
        Toast.makeText(requireContext(), "Earthquake alerts disabled", Toast.LENGTH_SHORT).show();
    }

    private void setupSignOut() {
        mBinding.btnSignOut.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sign out")
                    .setMessage("Are you sure you want to sign out of QuakeWatch?")
                    .setPositiveButton("Sign out", (dialog, which) -> signOut())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        } else {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
    }

    private void signOut() {
        AuthRepository.getInstance().signOut();
        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
