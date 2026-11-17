package com.akash.quakewatch.ui.home;

import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.model.Quake;
import com.akash.quakewatch.util.GeoUtils;
import com.akash.quakewatch.util.MagnitudeUtils;
import com.akash.quakewatch.util.TimeUtils;

import java.util.Locale;

/**
 * QuakeAdapter binds earthquake list items into the Home dashboard RecyclerView.
 * It presents severity-colored badges, location details, relative timestamps,
 * focal depth, and calculated distance from the user.
 */
public class QuakeAdapter extends ListAdapter<Quake, QuakeAdapter.QuakeViewHolder> {

    public interface OnQuakeClickListener {
        void onQuakeClick(Quake quake);
    }

    private final OnQuakeClickListener mListener;
    private Location mUserLocation = null;
    private java.util.Map<String, Integer> mFeltCounts = java.util.Collections.emptyMap();

    private static final DiffUtil.ItemCallback<Quake> DIFF_CALLBACK = new DiffUtil.ItemCallback<Quake>() {
        @Override
        public boolean areItemsTheSame(@NonNull Quake oldItem, @NonNull Quake newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Quake oldItem, @NonNull Quake newItem) {
            return oldItem.getMagnitude() == newItem.getMagnitude()
                    && oldItem.getTimeMs() == newItem.getTimeMs()
                    && oldItem.getDepthKm() == newItem.getDepthKm()
                    && oldItem.getPlace().equals(newItem.getPlace());
        }
    };

    public QuakeAdapter(OnQuakeClickListener listener) {
        super(DIFF_CALLBACK);
        this.mListener = listener;
    }

    public void setUserLocation(Location location) {
        this.mUserLocation = location;
        notifyDataSetChanged();
    }

    public void setFeltCounts(java.util.Map<String, Integer> counts) {
        this.mFeltCounts = counts != null ? counts : java.util.Collections.emptyMap();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuakeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quake_row, parent, false);
        return new QuakeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuakeViewHolder holder, int position) {
        Quake quake = getItem(position);
        holder.bind(quake);
    }

    class QuakeViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvBadgeMag;
        private final TextView tvPlace;
        private final TextView tvDetails;
        private final TextView tvFeltChip;

        QuakeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadgeMag = itemView.findViewById(R.id.tv_badge_mag);
            tvPlace = itemView.findViewById(R.id.tv_place);
            tvDetails = itemView.findViewById(R.id.tv_details);
            tvFeltChip = itemView.findViewById(R.id.tv_felt_chip);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onQuakeClick(getItem(position));
                }
            });
        }

        void bind(Quake quake) {
            // Magnitude Badge with Severity Color
            tvBadgeMag.setText(MagnitudeUtils.formatMagnitude(quake.getMagnitude()));
            MagnitudeUtils.Severity sev = MagnitudeUtils.severity(quake.getMagnitude());
            int color = ContextCompat.getColor(itemView.getContext(), sev.getColorRes());

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            float radius = itemView.getResources().getDimension(R.dimen.mag_badge_radius);
            badgeBg.setCornerRadius(radius);
            badgeBg.setColor(color);
            tvBadgeMag.setBackground(badgeBg);

            // Title
            tvPlace.setText(quake.getPlace());

            // Details subtitle: time ago | depth km | distance if known
            String timeAgo = TimeUtils.timeAgo(quake.getTimeMs(), System.currentTimeMillis());
            String depth = String.format(Locale.US, "%.1f km", quake.getDepthKm());
            StringBuilder detailBuilder = new StringBuilder();
            detailBuilder.append(timeAgo).append(" \u2022 ").append(depth);

            if (mUserLocation != null) {
                double distKm = GeoUtils.distanceKm(mUserLocation.getLatitude(), mUserLocation.getLongitude(),
                        quake.getLatitude(), quake.getLongitude());
                detailBuilder.append(" \u2022 ").append(String.format(Locale.US, "%.0f km away", distKm));
            }

            tvDetails.setText(detailBuilder.toString());

            // Community Felt Chip (<n> felt)
            int feltCount = mFeltCounts.getOrDefault(quake.getId(), 0);
            if (feltCount > 0) {
                tvFeltChip.setVisibility(View.VISIBLE);
                tvFeltChip.setText(String.format(Locale.US, "%d felt", feltCount));
            } else {
                tvFeltChip.setVisibility(View.GONE);
            }
        }
    }
}
