package com.example.skyeos.ui.fragment;

import com.example.skyeos.data.auth.CurrentUserContext;

import com.example.skyeos.data.db.LifeOsDatabase;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.example.skyeos.domain.usecase.LifeOsUseCases;


import com.example.skyeos.R;
import com.example.skyeos.ai.AiApiConfig;
import com.example.skyeos.domain.model.RecentRecordItem;
import com.example.skyeos.domain.model.WindowOverview;
import com.example.skyeos.ui.util.UiFormatters;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AndroidEntryPoint
public class AiChatFragment extends Fragment {

    @Inject
    CurrentUserContext userContext;

    @Inject
    LifeOsDatabase database;

    @Inject
    LifeOsUseCases useCases;

    @Inject
    com.example.skyeos.ai.AiApiConfigStore configStore;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LocalDate selectedDate = LocalDate.now();

    private MaterialButton btnDate;
    private MaterialButton btnRefresh;
    private MaterialButton btnSend;
    private TextView tvContext;
    private TextView tvTranscript;
    private TextInputEditText etInput;
    private final StringBuilder transcriptBuilder = new StringBuilder();
    private String latestContext = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        btnDate = view.findViewById(R.id.btn_ai_chat_date);
        btnRefresh = view.findViewById(R.id.btn_ai_chat_refresh);
        btnSend = view.findViewById(R.id.btn_ai_chat_send);
        tvContext = view.findViewById(R.id.tv_ai_chat_context);
        tvTranscript = view.findViewById(R.id.tv_ai_chat_transcript);
        etInput = view.findViewById(R.id.et_ai_chat_input);

        btnDate.setOnClickListener(v -> openDatePicker());
        btnRefresh.setOnClickListener(v -> loadContext());
        btnSend.setOnClickListener(v -> sendMessage());
        refreshDateButton();
        loadContext();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }

    private void openDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (picker, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    refreshDateButton();
                    loadContext();
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());
        dialog.show();
    }

    private void refreshDateButton() {
        btnDate.setText(getString(R.string.ai_chat_pick_date_value, selectedDate.toString()));
    }

    private void loadContext() {
        tvContext.setText(R.string.ai_chat_context_loading);
        executor.execute(() -> {
            String day = selectedDate.toString();
            WindowOverview overview = useCases.getOverview.execute(day, "day");
            List<RecentRecordItem> rows = useCases.getRecordsForDate.execute(day, 40);
            String context = buildContext(day, overview, rows);
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                latestContext = context;
                tvContext.setText(context);
            });
        });
    }

    private String buildContext(String day, WindowOverview overview, List<RecentRecordItem> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(day)
                .append(" | ")
                .append(getString(R.string.ai_chat_context_summary_format,
                        UiFormatters.duration(requireContext(), overview.totalTimeMinutes),
                        UiFormatters.duration(requireContext(), overview.totalWorkMinutes),
                        UiFormatters.duration(requireContext(), overview.totalLearningMinutes),
                        UiFormatters.yuan(requireContext(), overview.totalIncomeCents),
                        UiFormatters.yuan(requireContext(), overview.totalExpenseCents)))
                .append('\n');
        if (rows == null || rows.isEmpty()) {
            sb.append(getString(R.string.ai_chat_context_no_records));
            return sb.toString();
        }
        int count = 0;
        for (RecentRecordItem row : rows) {
            if (row == null) {
                continue;
            }
            sb.append("- ")
                    .append(row.type == null ? "unknown" : row.type)
                    .append(" | ")
                    .append(row.title == null ? "" : row.title)
                    .append(" | ")
                    .append(row.detail == null ? "" : row.detail)
                    .append('\n');
            count++;
            if (count >= 12) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private void sendMessage() {
        String userInput = etInput.getText() == null ? "" : etInput.getText().toString().trim();
        if (userInput.isEmpty()) {
            Snackbar.make(requireView(), R.string.ai_chat_empty_input, Snackbar.LENGTH_SHORT).show();
            return;
        }
        appendTranscript(getString(R.string.ai_chat_you_prefix, userInput));
        etInput.setText("");
        btnSend.setEnabled(false);
        btnSend.setText(R.string.ai_chat_waiting);

        executor.execute(() -> {
            String reply;
            try {
                reply = requestAiReply(userInput);
            } catch (Exception e) {
                reply = getString(R.string.ai_chat_error_prefix, safeErrorMessage(e));
            }
            final String finalReply = reply;
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                appendTranscript(getString(R.string.ai_chat_ai_prefix, finalReply));
                btnSend.setEnabled(true);
                btnSend.setText(R.string.ai_chat_send);
            });
        });
    }

    private void appendTranscript(String line) {
        if (transcriptBuilder.length() > 0) {
            transcriptBuilder.append("\n\n");
        }
        transcriptBuilder.append(line);
        tvTranscript.setText(transcriptBuilder.toString());
    }

    private String requestAiReply(String question) throws Exception {
        AiApiConfig config = configStore.load();
        if (config == null || !config.isValid()) {
            return getString(R.string.ai_chat_config_missing);
        }

        String endpoint = buildChatEndpoint(config.resolvedBaseUrl());
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject body = new JSONObject()
                .put("model", config.resolvedModel())
                .put("temperature", 0.2)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content",
                                        "你是 SkyeOS 的复盘教练。请基于给定日期上下文，先复述事实，再给出可执行建议。不要编造未出现的数据。"))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", "日期上下文：\n" + latestContext + "\n\n用户问题：\n" + question)));

        try (OutputStream out = conn.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(body.toString());
            writer.flush();
        }

        int code = conn.getResponseCode();
        String raw = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("LLM API failed (" + code + "): " + raw);
        }
        JSONObject response = new JSONObject(raw);
        return extractAssistantContent(response);
    }

    private static String extractAssistantContent(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("LLM API response has no choices");
        }
        JSONObject first = choices.optJSONObject(0);
        if (first == null) {
            throw new IllegalStateException("LLM API first choice is invalid");
        }
        JSONObject message = first.optJSONObject("message");
        if (message == null) {
            throw new IllegalStateException("LLM API response has no message");
        }
        Object contentObj = message.opt("content");
        if (contentObj instanceof String) {
            return ((String) contentObj).trim();
        }
        if (contentObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) contentObj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                Object part = arr.opt(i);
                if (part instanceof JSONObject) {
                    String t = ((JSONObject) part).optString("text", "");
                    if (!t.isEmpty()) {
                        sb.append(t);
                    }
                } else if (part instanceof String) {
                    sb.append((String) part);
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private static String normalizeBaseUrl(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String buildChatEndpoint(String rawBaseUrl) {
        String base = normalizeBaseUrl(rawBaseUrl);
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    private static String readBody(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String safeErrorMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return "unknown error";
        }
        String message = e.getMessage().trim();
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }
}
