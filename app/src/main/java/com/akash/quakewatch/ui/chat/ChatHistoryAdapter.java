package com.akash.quakewatch.ui.chat;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akash.quakewatch.data.model.ChatSession;
import com.akash.quakewatch.databinding.ItemChatSessionBinding;
import com.akash.quakewatch.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ChatHistoryAdapter renders past chat conversation threads in a recents drawer/bottom sheet,
 * formatting each session with its topic title, relative recency, and total message count.
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClicked(ChatSession session);
        void onSessionDeleted(ChatSession session);
    }

    private final List<ChatSession> mSessions = new ArrayList<>();
    private final OnSessionClickListener mListener;

    public ChatHistoryAdapter(OnSessionClickListener listener) {
        this.mListener = listener;
    }

    public void setSessions(List<ChatSession> sessions) {
        mSessions.clear();
        if (sessions != null) {
            mSessions.addAll(sessions);
        }
        notifyDataSetChanged();
    }

    public void removeSession(ChatSession session) {
        int pos = mSessions.indexOf(session);
        if (pos >= 0) {
            mSessions.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatSessionBinding binding = ItemChatSessionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SessionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(mSessions.get(position));
    }

    @Override
    public int getItemCount() {
        return mSessions.size();
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatSessionBinding mBinding;

        SessionViewHolder(ItemChatSessionBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        void bind(ChatSession session) {
            mBinding.tvSessionTitle.setText(session.getTitle());

            String timeAgo = TimeUtils.timeAgo(session.getUpdatedMs(), System.currentTimeMillis());
            int count = session.getMessageCount();
            String meta = String.format(Locale.US, "%s \u00B7 %d %s",
                    timeAgo, count, count == 1 ? "message" : "messages");
            mBinding.tvSessionMeta.setText(meta);

            mBinding.getRoot().setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onSessionClicked(session);
                }
            });

            mBinding.btnDeleteSession.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onSessionDeleted(session);
                }
            });
        }
    }
}
