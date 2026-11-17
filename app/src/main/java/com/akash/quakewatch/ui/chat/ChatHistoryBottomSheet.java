package com.akash.quakewatch.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.akash.quakewatch.data.QuakeRepository;
import com.akash.quakewatch.data.model.ChatSession;
import com.akash.quakewatch.databinding.BottomSheetChatHistoryBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * ChatHistoryBottomSheet displays the ChatGPT/Claude-style recents drawer,
 * allowing users to view, resume, or delete past earthquake safety conversations.
 */
public class ChatHistoryBottomSheet extends BottomSheetDialogFragment {

    public interface HistoryListener {
        void onSessionSelected(String sessionId, String sessionTitle);
        void onNewChatClicked();
    }

    private BottomSheetChatHistoryBinding mBinding;
    private ChatHistoryAdapter mAdapter;
    private HistoryListener mListener;

    public static ChatHistoryBottomSheet newInstance() {
        return new ChatHistoryBottomSheet();
    }

    public void setListener(HistoryListener listener) {
        this.mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = BottomSheetChatHistoryBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new ChatHistoryAdapter(new ChatHistoryAdapter.OnSessionClickListener() {
            @Override
            public void onSessionClicked(ChatSession session) {
                if (mListener != null) {
                    mListener.onSessionSelected(session.getId(), session.getTitle());
                }
                dismiss();
            }

            @Override
            public void onSessionDeleted(ChatSession session) {
                QuakeRepository.getInstance(requireContext()).deleteChatSession(session.getId(), () -> {
                    if (mBinding != null) {
                        mAdapter.removeSession(session);
                        if (mAdapter.getItemCount() == 0) {
                            mBinding.layoutEmptyHistory.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        });

        mBinding.rvChatSessions.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvChatSessions.setAdapter(mAdapter);

        mBinding.btnSheetNewChat.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onNewChatClicked();
            }
            dismiss();
        });

        loadHistory();
    }

    private void loadHistory() {
        QuakeRepository.getInstance(requireContext()).loadChatSessions(sessions -> {
            if (mBinding == null) return;
            if (sessions == null || sessions.isEmpty()) {
                mBinding.layoutEmptyHistory.setVisibility(View.VISIBLE);
                mBinding.rvChatSessions.setVisibility(View.GONE);
            } else {
                mBinding.layoutEmptyHistory.setVisibility(View.GONE);
                mBinding.rvChatSessions.setVisibility(View.VISIBLE);
                mAdapter.setSessions(sessions);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
