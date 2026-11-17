package com.akash.quakewatch.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.AuthRepository;
import com.akash.quakewatch.data.SettingsStore;
import com.akash.quakewatch.databinding.ActivityOnboardingBinding;
import com.akash.quakewatch.ui.auth.AuthActivity;
import com.akash.quakewatch.ui.main.MainActivity;

/**
 * OnboardingActivity introduces first-time users to QuakeWatch across three slides.
 * It tracks completion in SettingsStore and guides users to authentication or the dashboard.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding mBinding;

    private static class Slide {
        final String title;
        final String description;
        final int iconRes;

        Slide(String title, String description, int iconRes) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
        }
    }

    private final Slide[] mSlides = new Slide[]{
            new Slide(
                    "See what is shaking",
                    "Track live earthquakes worldwide and across your region on a clear, interactive field map.",
                    R.drawable.ic_map
            ),
            new Slide(
                    "Understand it",
                    "Get plain-language AI explanations, safety tips, and magnitude breakdowns without complex jargon.",
                    R.drawable.ic_insights
            ),
            new Slide(
                    "Stay alert",
                    "Receive timely notifications for significant earthquakes and submit live community felt reports.",
                    R.drawable.ic_notifications
            )
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.onboardingRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mBinding.viewPager.setAdapter(new OnboardingAdapter());
        mBinding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicators(position);
            }
        });

        mBinding.btnSkip.setOnClickListener(v -> completeOnboarding());
        mBinding.btnAction.setOnClickListener(v -> {
            int current = mBinding.viewPager.getCurrentItem();
            if (current < mSlides.length - 1) {
                mBinding.viewPager.setCurrentItem(current + 1, true);
            } else {
                completeOnboarding();
            }
        });
    }

    private void updateIndicators(int position) {
        mBinding.dot0.setBackgroundResource(position == 0 ? R.drawable.bg_indicator_dot_active : R.drawable.bg_indicator_dot_inactive);
        mBinding.dot1.setBackgroundResource(position == 1 ? R.drawable.bg_indicator_dot_active : R.drawable.bg_indicator_dot_inactive);
        mBinding.dot2.setBackgroundResource(position == 2 ? R.drawable.bg_indicator_dot_active : R.drawable.bg_indicator_dot_inactive);

        if (position == mSlides.length - 1) {
            mBinding.btnAction.setText("Get started");
            mBinding.btnSkip.setVisibility(View.INVISIBLE);
        } else {
            mBinding.btnAction.setText("Next");
            mBinding.btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void completeOnboarding() {
        SettingsStore.getInstance(this).setOnboardingCompleted(true);
        Intent intent;
        if (AuthRepository.getInstance().isSignedIn()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, AuthActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

        @NonNull
        @Override
        public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_slide, parent, false);
            return new SlideViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
            Slide slide = mSlides[position];
            holder.title.setText(slide.title);
            holder.description.setText(slide.description);
            holder.icon.setImageResource(slide.iconRes);
        }

        @Override
        public int getItemCount() {
            return mSlides.length;
        }

        class SlideViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView description;
            final ImageView icon;

            SlideViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.slide_title);
                description = itemView.findViewById(R.id.slide_description);
                icon = itemView.findViewById(R.id.slide_icon);
            }
        }
    }
}
