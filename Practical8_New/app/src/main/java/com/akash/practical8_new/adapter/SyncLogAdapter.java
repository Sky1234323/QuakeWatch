package com.akash.practical8_new.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.practical8_new.R;
import com.akash.practical8_new.model.LocationModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying live real-time location logs synced with Firebase.
 */
public class SyncLogAdapter extends RecyclerView.Adapter<SyncLogAdapter.LogViewHolder> {

    private final List<LocationModel> items;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss a, dd MMM", Locale.getDefault());

    public SyncLogAdapter(List<LocationModel> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sync_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LocationModel item = items.get(position);
        holder.tvCoords.setText(String.format(Locale.US, "Lat: %.5f, Lng: %.5f", item.getLatitude(), item.getLongitude()));
        holder.tvTime.setText(dateFormat.format(new Date(item.getTimestamp())));
        holder.tvAddress.setText(item.getAddress() != null && !item.getAddress().isEmpty() ? item.getAddress() : "Location coordinates recorded");
        holder.tvMeta.setText(String.format(Locale.US, "Accuracy: ±%.1fm • Device: %s", item.getAccuracy(), item.getDeviceName()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCoords;
        final TextView tvTime;
        final TextView tvAddress;
        final TextView tvMeta;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCoords = itemView.findViewById(R.id.tv_item_coords);
            tvTime = itemView.findViewById(R.id.tv_item_time);
            tvAddress = itemView.findViewById(R.id.tv_item_address);
            tvMeta = itemView.findViewById(R.id.tv_item_meta);
        }
    }
}
