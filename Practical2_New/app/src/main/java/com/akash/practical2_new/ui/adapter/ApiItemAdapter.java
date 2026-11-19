package com.akash.practical2_new.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.practical2_new.R;
import com.akash.practical2_new.data.model.ApiItem;

import java.util.ArrayList;
import java.util.List;

/**
 * ApiItemAdapter displays REST API records in a structured card list.
 */
public class ApiItemAdapter extends RecyclerView.Adapter<ApiItemAdapter.ViewHolder> {

    private final List<ApiItem> mItems = new ArrayList<>();

    public void setItems(List<ApiItem> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_api_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApiItem item = mItems.get(position);
        holder.tvCategoryBadge.setText(item.getCategory());
        holder.tvIdLabel.setText("ID: " + item.getId());
        holder.tvTitle.setText(item.getTitle());
        holder.tvBody.setText(item.getBody());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategoryBadge;
        final TextView tvIdLabel;
        final TextView tvTitle;
        final TextView tvBody;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryBadge = itemView.findViewById(R.id.tv_category_badge);
            tvIdLabel = itemView.findViewById(R.id.tv_id_label);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvBody = itemView.findViewById(R.id.tv_body);
        }
    }
}
