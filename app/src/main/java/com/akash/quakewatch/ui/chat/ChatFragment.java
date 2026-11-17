package com.akash.quakewatch.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.akash.quakewatch.ai.GeminiClient;
import com.akash.quakewatch.data.QuakeRepository;
import com.akash.quakewatch.data.model.ChatMessage;
import com.akash.quakewatch.data.model.ChatSession;
import com.akash.quakewatch.databinding.FragmentChatBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ChatFragment provides an interactive conversational interface with the Gemini-powered
 * QuakeWatch Assistant. It supports real-time SSE token streaming, suggestion chips,
 * persistent multi-session chat history (recents drawer), and emergency service advisories.
 */
public class ChatFragment extends Fragment {

    private FragmentChatBinding mBinding;
    private ChatAdapter mAdapter;
    private boolean mIsGenerating = false;

    private String mCurrentSessionId = null;
    private String mCurrentSessionTitle = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentChatBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupControls();

        // Restore most recent session if adapter is empty so user never starts from zero unintentionally
        if (mAdapter.getItemCount() == 0 && mCurrentSessionId == null) {
            restoreMostRecentSession();
        }
    }

    private void setupRecyclerView() {
        mAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        mBinding.rvChatMessages.setLayoutManager(layoutManager);
        mBinding.rvChatMessages.setAdapter(mAdapter);
    }

    private void setupControls() {
        // Send button click
        mBinding.btnSendChat.setOnClickListener(v -> sendMessage(mBinding.etChatInput.getText().toString().trim()));

        // Keyboard Action Done / Send
        mBinding.etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(mBinding.etChatInput.getText().toString().trim());
                return true;
            }
            return false;
        });

        // History / Recents Drawer action
        mBinding.btnChatHistory.setOnClickListener(v -> showHistoryBottomSheet());

        // New Chat toolbar action
        mBinding.btnNewChat.setOnClickListener(v -> startNewChat());

        // Suggestion chips
        mBinding.chipSuggestionDuring.setOnClickListener(v ->
                sendMessage("What should I do during an earthquake?"));

        mBinding.chipSuggestionAftershocks.setOnClickListener(v ->
                sendMessage("Why do aftershocks happen?"));

        mBinding.chipSuggestionMagnitude.setOnClickListener(v ->
                sendMessage("What is the difference between magnitude and intensity?"));

        mBinding.chipSuggestionKit.setOnClickListener(v ->
                sendMessage("How should I build an earthquake emergency kit?"));
    }

    private void restoreMostRecentSession() {
        QuakeRepository.getInstance(requireContext()).loadChatSessions(sessions -> {
            if (mBinding == null || !isAdded()) return;
            if (sessions != null && !sessions.isEmpty()) {
                ChatSession latest = sessions.get(0);
                loadSession(latest.getId(), latest.getTitle());
            } else {
                mBinding.scrollSuggestions.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showHistoryBottomSheet() {
        ChatHistoryBottomSheet sheet = ChatHistoryBottomSheet.newInstance();
        sheet.setListener(new ChatHistoryBottomSheet.HistoryListener() {
            @Override
            public void onSessionSelected(String sessionId, String sessionTitle) {
                loadSession(sessionId, sessionTitle);
            }

            @Override
            public void onNewChatClicked() {
                startNewChat();
            }
        });
        sheet.show(getChildFragmentManager(), "chat_history");
    }

    private void startNewChat() {
        if (mIsGenerating) return;
        mCurrentSessionId = null;
        mCurrentSessionTitle = null;
        mAdapter.clearMessages();
        mBinding.scrollSuggestions.setVisibility(View.VISIBLE);
    }

    private void loadSession(String sessionId, String sessionTitle) {
        if (mIsGenerating) return;
        mCurrentSessionId = sessionId;
        mCurrentSessionTitle = sessionTitle;

        QuakeRepository.getInstance(requireContext()).loadSessionMessages(sessionId, messages -> {
            if (mBinding == null || !isAdded()) return;
            mAdapter.setMessages(messages);
            if (messages == null || messages.isEmpty()) {
                mBinding.scrollSuggestions.setVisibility(View.VISIBLE);
            } else {
                mBinding.scrollSuggestions.setVisibility(View.GONE);
                mBinding.rvChatMessages.scrollToPosition(messages.size() - 1);
            }
        });
    }

    private void sendMessage(String userText) {
        if (userText == null || userText.trim().isEmpty() || mIsGenerating) {
            return;
        }

        mIsGenerating = true;
        mBinding.etChatInput.setText("");
        mBinding.scrollSuggestions.setVisibility(View.GONE);

        // Ensure session exists
        if (mCurrentSessionId == null) {
            mCurrentSessionId = UUID.randomUUID().toString();
            String title = userText.length() > 36 ? userText.substring(0, 36) + "..." : userText;
            mCurrentSessionTitle = title;
        }

        // Add user message
        ChatMessage userMsg = new ChatMessage(ChatMessage.ROLE_USER, userText, 0.0, false);
        mAdapter.addMessage(userMsg);

        // Persist user message to SQLite
        QuakeRepository.getInstance(requireContext()).saveChatMessage(mCurrentSessionId, userMsg, mCurrentSessionTitle);

        // Take snapshot of history for Gemini (BEFORE adding empty placeholder assistant message)
        List<ChatMessage> promptHistory = new ArrayList<>(mAdapter.getMessages());

        // Add placeholder assistant message to UI for live streaming
        ChatMessage assistantMsg = new ChatMessage(ChatMessage.ROLE_MODEL, "", 0.0, true);
        mAdapter.addMessage(assistantMsg);
        mBinding.rvChatMessages.scrollToPosition(mAdapter.getItemCount() - 1);

        final String activeSessionId = mCurrentSessionId;
        final String activeSessionTitle = mCurrentSessionTitle;

        // Stream conversational response from Gemini
        GeminiClient.getInstance().streamChat(promptHistory, new GeminiClient.StreamCallback() {
            @Override
            public void onChunk(String chunk) {
                if (mBinding == null || !isAdded()) return;
                mAdapter.updateLastMessage(chunk);
                mBinding.rvChatMessages.scrollToPosition(mAdapter.getItemCount() - 1);
            }

            @Override
            public void onComplete(String fullText, double responseTimeSec) {
                if (mBinding == null || !isAdded()) return;
                mIsGenerating = false;
                mAdapter.completeLastMessage(fullText, responseTimeSec);
                mBinding.rvChatMessages.scrollToPosition(mAdapter.getItemCount() - 1);

                // Persist assistant message to SQLite
                ChatMessage completed = new ChatMessage(ChatMessage.ROLE_MODEL, fullText, responseTimeSec, false);
                QuakeRepository.getInstance(requireContext()).saveChatMessage(activeSessionId, completed, activeSessionTitle);
            }

            @Override
            public void onError(String errorMessage) {
                if (mBinding == null || !isAdded()) return;
                mIsGenerating = false;
                mAdapter.completeLastMessage(errorMessage, 0.0);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
