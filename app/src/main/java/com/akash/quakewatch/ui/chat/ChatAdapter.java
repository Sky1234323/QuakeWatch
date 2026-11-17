package com.akash.quakewatch.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.quakewatch.R;
import com.akash.quakewatch.data.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatAdapter binds conversational turns into user and assistant speech bubbles,
 * rendering word-by-word streaming updates and response latency metrics.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> mMessages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        mMessages.add(message);
        notifyItemInserted(mMessages.size() - 1);
    }

    public void updateLastMessage(String chunk) {
        if (!mMessages.isEmpty()) {
            int lastIdx = mMessages.size() - 1;
            ChatMessage last = mMessages.get(lastIdx);
            last.appendText(chunk);
            notifyItemChanged(lastIdx);
        }
    }

    public void completeLastMessage(String fullText, double responseTimeSec) {
        if (!mMessages.isEmpty()) {
            int lastIdx = mMessages.size() - 1;
            ChatMessage last = mMessages.get(lastIdx);
            if (fullText != null && !fullText.trim().isEmpty()) {
                last.setText(fullText);
            }
            last.setResponseTimeSec(responseTimeSec);
            last.setStreaming(false);
            notifyItemChanged(lastIdx);
        }
    }

    public void setMessages(List<ChatMessage> messages) {
        mMessages.clear();
        if (messages != null) {
            mMessages.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void clearMessages() {
        mMessages.clear();
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return mMessages;
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = mMessages.get(position);
        holder.bind(msg);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout layoutUser;
        private final TextView tvUserText;
        private final LinearLayout layoutAssistant;
        private final TextView tvAssistantText;
        private final TextView tvResponseCaption;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUser = itemView.findViewById(R.id.layout_user_message);
            tvUserText = itemView.findViewById(R.id.tv_user_text);
            layoutAssistant = itemView.findViewById(R.id.layout_assistant_message);
            tvAssistantText = itemView.findViewById(R.id.tv_assistant_text);
            tvResponseCaption = itemView.findViewById(R.id.tv_response_caption);
        }

        void bind(ChatMessage message) {
            if (message.isUser()) {
                layoutUser.setVisibility(View.VISIBLE);
                layoutAssistant.setVisibility(View.GONE);
                tvUserText.setText(message.getText());
            } else {
                layoutUser.setVisibility(View.GONE);
                layoutAssistant.setVisibility(View.VISIBLE);

                if (message.isStreaming()) {
                    if (message.getText().isEmpty()) {
                        tvAssistantText.setText("Thinking...");
                        tvResponseCaption.setVisibility(View.GONE);
                    } else {
                        tvAssistantText.setText(message.getText());
                        tvResponseCaption.setVisibility(View.VISIBLE);
                        tvResponseCaption.setText("Streaming answer...");
                    }
                } else {
                    tvAssistantText.setText(message.getText());
                    tvResponseCaption.setVisibility(View.VISIBLE);
                    tvResponseCaption.setText(String.format(Locale.US, "Response time: %.1f s", message.getResponseTimeSec()));
                }
            }
        }
    }
}
