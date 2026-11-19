package com.akash.practical2_new.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.practical2_new.R;
import com.akash.practical2_new.data.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatAdapter renders the message bubbles for user questions and Gemini AI answers.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatMessage> mMessages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        mMessages.add(message);
        notifyItemInserted(mMessages.size() - 1);
    }

    public void clearMessages() {
        mMessages.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = mMessages.get(position);
        if (message.isUser()) {
            holder.layoutUser.setVisibility(View.VISIBLE);
            holder.layoutAi.setVisibility(View.GONE);
            holder.tvUserText.setText(message.getText());
        } else {
            holder.layoutUser.setVisibility(View.GONE);
            holder.layoutAi.setVisibility(View.VISIBLE);
            holder.tvAiText.setText(message.getText());
            if (message.getLatencyMs() > 0) {
                double seconds = message.getLatencyMs() / 1000.0;
                holder.tvLatency.setText(String.format(Locale.US, "Answered in %.2fs", seconds));
                holder.tvLatency.setVisibility(View.VISIBLE);
            } else {
                holder.tvLatency.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout layoutUser;
        final LinearLayout layoutAi;
        final TextView tvUserText;
        final TextView tvAiText;
        final TextView tvLatency;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUser = itemView.findViewById(R.id.layout_user_container);
            layoutAi = itemView.findViewById(R.id.layout_ai_container);
            tvUserText = itemView.findViewById(R.id.tv_user_text);
            tvAiText = itemView.findViewById(R.id.tv_ai_text);
            tvLatency = itemView.findViewById(R.id.tv_latency);
        }
    }
}
