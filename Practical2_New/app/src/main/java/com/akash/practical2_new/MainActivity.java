package com.akash.practical2_new;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.akash.practical2_new.data.api.GeminiAiClient;
import com.akash.practical2_new.data.api.RestApiClient;
import com.akash.practical2_new.data.model.ApiItem;
import com.akash.practical2_new.data.model.ChatMessage;
import com.akash.practical2_new.databinding.ActivityMainBinding;
import com.akash.practical2_new.ui.adapter.ApiItemAdapter;
import com.akash.practical2_new.ui.adapter.ChatAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * MainActivity coordinates Practical No. 02:
 * 1. REST API Integration: Fetches live data via HttpURLConnection and displays formatted JSON & raw payload.
 * 2. AI QA Assistant: Fast, responsive English Q&A assistant powered by Google Gemini API.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding mBinding;
    private RestApiClient mRestApiClient;
    private GeminiAiClient mGeminiAiClient;

    private ApiItemAdapter mApiAdapter;
    private ChatAdapter mChatAdapter;

    private String mLatestRawJson = "";
    private String mCurrentEndpoint = RestApiClient.ENDPOINT_ENGLISH_POSTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.mainRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mRestApiClient = new RestApiClient();
        mGeminiAiClient = new GeminiAiClient();

        setupTabs();
        setupRestApiSection();
        setupAiQaSection();
    }

    private void setupTabs() {
        mBinding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    mBinding.layoutRestSection.setVisibility(View.VISIBLE);
                    mBinding.layoutAiSection.setVisibility(View.GONE);
                } else {
                    mBinding.layoutRestSection.setVisibility(View.GONE);
                    mBinding.layoutAiSection.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    // ================= 1. REST API INTEGRATION =================

    private void setupRestApiSection() {
        mApiAdapter = new ApiItemAdapter();
        mBinding.rvApiItems.setLayoutManager(new LinearLayoutManager(this));
        mBinding.rvApiItems.setAdapter(mApiAdapter);

        // Radio group listener for endpoint selection
        mBinding.rgEndpoints.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_usgs) {
                mCurrentEndpoint = RestApiClient.ENDPOINT_USGS;
            } else {
                mCurrentEndpoint = RestApiClient.ENDPOINT_ENGLISH_POSTS;
            }
        });

        mBinding.btnFetchRest.setOnClickListener(v -> executeRestFetch());
        mBinding.btnViewRawJson.setOnClickListener(v -> showRawJsonBottomSheet());
    }

    private void executeRestFetch() {
        setRestLoading(true);
        mRestApiClient.fetchData(mCurrentEndpoint, new RestApiClient.ApiCallback() {
            @Override
            public void onSuccess(List<ApiItem> items, String rawJson, int statusCode, long durationMs) {
                setRestLoading(false);
                mLatestRawJson = rawJson;
                mBinding.btnViewRawJson.setEnabled(true);
                mBinding.tvEmptyState.setVisibility(View.GONE);

                mApiAdapter.setItems(items);

                // Update Status Banner
                mBinding.layoutStatusBanner.setVisibility(View.VISIBLE);
                mBinding.tvStatusCode.setText(String.format(Locale.US, "HTTP %d OK", statusCode));
                mBinding.tvStatusMeta.setText(String.format(Locale.US, "%d ms | %d items parsed", durationMs, items.size()));

                Snackbar.make(mBinding.getRoot(), "Fetched " + items.size() + " items successfully!", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                setRestLoading(false);
                mBinding.layoutStatusBanner.setVisibility(View.GONE);
                Snackbar.make(mBinding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setRestLoading(boolean loading) {
        mBinding.progressRest.setVisibility(loading ? View.VISIBLE : View.GONE);
        mBinding.btnFetchRest.setEnabled(!loading);
    }

    private void showRawJsonBottomSheet() {
        if (TextUtils.isEmpty(mLatestRawJson)) {
            Toast.makeText(this, "No JSON fetched yet", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_raw_json, null);
        dialog.setContentView(dialogView);

        TextView tvMeta = dialogView.findViewById(R.id.tv_json_meta);
        TextView tvContent = dialogView.findViewById(R.id.tv_raw_json_content);
        View btnCopy = dialogView.findViewById(R.id.btn_copy_json);
        View btnClose = dialogView.findViewById(R.id.btn_close_dialog);

        tvMeta.setText("Source: " + mCurrentEndpoint + " (Size: " + mLatestRawJson.length() + " chars)");

        // Pretty format JSON if possible
        String prettyJson = mLatestRawJson;
        try {
            String trimmed = mLatestRawJson.trim();
            if (trimmed.startsWith("{")) {
                prettyJson = new JSONObject(trimmed).toString(2);
            } else if (trimmed.startsWith("[")) {
                prettyJson = new JSONArray(trimmed).toString(2);
            }
        } catch (Exception ignored) {
        }
        tvContent.setText(prettyJson);

        final String copyPayload = prettyJson;
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("REST_JSON", copyPayload));
                Toast.makeText(MainActivity.this, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ================= 2. GEMINI AI QA ASSISTANT =================

    private void setupAiQaSection() {
        mChatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        mBinding.rvChatMessages.setLayoutManager(layoutManager);
        mBinding.rvChatMessages.setAdapter(mChatAdapter);

        // Add Initial AI Welcome Message
        mChatAdapter.addMessage(new ChatMessage(
                "Hello! I am your Gemini AI Assistant for Practical 2.\nAsk me any question in English about REST APIs, JSON parsing, or Android mobile architecture for instant answers.",
                ChatMessage.Sender.AI,
                System.currentTimeMillis(),
                0
        ));

        // Setup Suggestion Chips
        mBinding.chipQ1.setOnClickListener(v -> submitQuestion("What is a REST API and why is it used in Android?"));
        mBinding.chipQ2.setOnClickListener(v -> submitQuestion("What is the difference between HTTP GET and POST methods?"));
        mBinding.chipQ3.setOnClickListener(v -> submitQuestion("How does JSON parsing work in Android using JSONObject and JSONArray?"));
        mBinding.chipQ4.setOnClickListener(v -> submitQuestion("Explain how HttpURLConnection works in Java for background network tasks."));

        // Setup Send Button
        mBinding.btnSendAi.setOnClickListener(v -> {
            String question = mBinding.etAiQuestion.getText() != null
                    ? mBinding.etAiQuestion.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(question)) {
                submitQuestion(question);
            }
        });
    }

    private void submitQuestion(String question) {
        mBinding.etAiQuestion.setText("");

        // Add user question to chat UI
        mChatAdapter.addMessage(new ChatMessage(question, ChatMessage.Sender.USER, System.currentTimeMillis(), 0));
        mBinding.rvChatMessages.smoothScrollToPosition(mChatAdapter.getItemCount() - 1);

        setAiLoading(true);

        mGeminiAiClient.askQuestion(question, new GeminiAiClient.AiCallback() {
            @Override
            public void onResponse(String answer, long latencyMs) {
                setAiLoading(false);
                mChatAdapter.addMessage(new ChatMessage(answer, ChatMessage.Sender.AI, System.currentTimeMillis(), latencyMs));
                mBinding.rvChatMessages.smoothScrollToPosition(mChatAdapter.getItemCount() - 1);
            }

            @Override
            public void onError(String errorMessage) {
                setAiLoading(false);
                mChatAdapter.addMessage(new ChatMessage("Error: " + errorMessage, ChatMessage.Sender.AI, System.currentTimeMillis(), 0));
                mBinding.rvChatMessages.smoothScrollToPosition(mChatAdapter.getItemCount() - 1);
            }
        });
    }

    private void setAiLoading(boolean loading) {
        mBinding.progressAi.setVisibility(loading ? View.VISIBLE : View.GONE);
        mBinding.btnSendAi.setEnabled(!loading);
        mBinding.etAiQuestion.setEnabled(!loading);
    }
}
