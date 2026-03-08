package com.example.skyeos.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppGraph;
import com.example.skyeos.R;
import com.example.skyeos.ai.AiApiConfig;
import com.example.skyeos.ai.AiApiProvider;
import com.example.skyeos.cloud.CloudSyncClient;
import com.example.skyeos.cloud.CloudSyncConfig;
import com.example.skyeos.cloud.CloudSyncConfigStore;
import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.model.TagItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.time.Instant;
import java.util.List;

public class SettingsFragment extends Fragment {

    private AppGraph graph;
    private CloudSyncConfigStore configStore;
    private CloudSyncClient cloudSyncClient;

    private TextInputEditText etServerUrl, etApiKey, etDeviceId, etDownloadFilename;
    private TextInputEditText etIdealHourlyYuan;
    private AutoCompleteTextView etAiProvider, etTagScope;
    private TextInputEditText etAiBaseUrl, etAiApiKey, etAiModel, etAiSystemPrompt;
    private TextInputEditText etTagName, etTagEmoji;
    private TextView tvStatus, tvRemoteList, tvAiTestResult, tvTagList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        graph = AppGraph.getInstance(requireContext());
        configStore = new CloudSyncConfigStore(requireContext());
        cloudSyncClient = new CloudSyncClient();

        etServerUrl = view.findViewById(R.id.et_server_url);
        etApiKey = view.findViewById(R.id.et_api_key);
        etDeviceId = view.findViewById(R.id.et_device_id);
        etDownloadFilename = view.findViewById(R.id.et_download_filename);
        etIdealHourlyYuan = view.findViewById(R.id.et_ideal_hourly_yuan);
        etAiProvider = view.findViewById(R.id.et_ai_provider);
        etAiBaseUrl = view.findViewById(R.id.et_ai_base_url);
        etAiApiKey = view.findViewById(R.id.et_ai_api_key);
        etAiModel = view.findViewById(R.id.et_ai_model);
        etAiSystemPrompt = view.findViewById(R.id.et_ai_system_prompt);
        etTagName = view.findViewById(R.id.et_tag_name);
        etTagEmoji = view.findViewById(R.id.et_tag_emoji);
        etTagScope = view.findViewById(R.id.et_tag_scope);
        tvStatus = view.findViewById(R.id.tv_cloud_status);
        tvRemoteList = view.findViewById(R.id.tv_remote_list);
        tvAiTestResult = view.findViewById(R.id.tv_ai_test_result);
        tvTagList = view.findViewById(R.id.tv_tag_list);

        // Load saved config
        CloudSyncConfig config = configStore.load();
        if (config.serverBaseUrl != null)
            etServerUrl.setText(config.serverBaseUrl);
        if (config.apiKey != null)
            etApiKey.setText(config.apiKey);
        if (config.deviceId != null)
            etDeviceId.setText(config.deviceId);
        AiApiConfig aiConfig = graph.aiApiConfigStore.load();
        setupAiProviderDropdown();
        setupTagScopeDropdown();
        etAiProvider.setText(aiConfig.provider.toPersistedValue(), false);
        etAiBaseUrl.setText(aiConfig.baseUrl);
        etAiApiKey.setText(aiConfig.apiKey);
        etAiModel.setText(aiConfig.model);
        etAiSystemPrompt.setText(aiConfig.resolvedSystemPrompt());
        etTagScope.setText("global", false);
        long idealHourlyCents = graph.useCases.getIdealHourlyRate.execute();
        if (idealHourlyCents > 0) {
            etIdealHourlyYuan.setText(String.format(java.util.Locale.US, "%.2f", idealHourlyCents / 100.0));
        }
        refreshTagList();

        MaterialButton btnSave = view.findViewById(R.id.btn_save_config);
        MaterialButton btnUpload = view.findViewById(R.id.btn_upload_backup);
        MaterialButton btnList = view.findViewById(R.id.btn_list_backups);
        MaterialButton btnDownload = view.findViewById(R.id.btn_download_restore);
        MaterialButton btnTestAi = view.findViewById(R.id.btn_test_ai);
        MaterialButton btnCreateTag = view.findViewById(R.id.btn_create_tag);

        btnSave.setOnClickListener(v -> saveConfig());
        btnUpload.setOnClickListener(v -> uploadLatestManualBackup());
        btnList.setOnClickListener(v -> listRemoteBackups());
        btnDownload.setOnClickListener(v -> downloadAndRestore());
        btnTestAi.setOnClickListener(v -> testAiConnection());
        btnCreateTag.setOnClickListener(v -> createTag());
    }

    private void saveConfig() {
        CloudSyncConfig config = collectConfig();
        configStore.save(config);
        AiApiConfig aiConfig = collectAiConfig();
        graph.aiApiConfigStore.save(aiConfig);
        long idealHourlyCents = parseYuanToCents(text(etIdealHourlyYuan));
        graph.useCases.setIdealHourlyRate.execute(idealHourlyCents);
        status("配置已保存 ✓（云同步+AI）");
    }

    private void testAiConnection() {
        AiApiConfig config = collectAiConfig();
        tvAiTestResult.setText("Ping中...");
        tvAiTestResult.setTextColor(getResources().getColor(android.R.color.darker_gray, null));

        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                String endpoint = config.resolvedBaseUrl();
                if (!endpoint.endsWith("/chat/completions") && !endpoint.endsWith("/v1")) {
                    endpoint = endpoint + "/v1/chat/completions";
                } else if (endpoint.endsWith("/v1")) {
                    endpoint = endpoint + "/chat/completions";
                }

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(endpoint)
                        .openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                org.json.JSONArray messages = new org.json.JSONArray();
                messages.put(new org.json.JSONObject().put("role", "user").put("content", "ping"));

                org.json.JSONObject body = new org.json.JSONObject()
                        .put("model", config.resolvedModel())
                        .put("temperature", 0.1)
                        .put("max_tokens", 10)
                        .put("messages", messages);

                try (java.io.OutputStream out = conn.getOutputStream();
                        java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(out,
                                java.nio.charset.StandardCharsets.UTF_8);
                        java.io.BufferedWriter writer = new java.io.BufferedWriter(osw)) {
                    writer.write(body.toString());
                    writer.flush();
                }

                int code = conn.getResponseCode();
                long duration = System.currentTimeMillis() - start;

                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        tvAiTestResult.setText("连接成功，耗时 " + duration + "ms");
                        tvAiTestResult.setTextColor(0xFF10B981); // Emerald Green
                    });
                } else {
                    java.io.InputStream errStr = conn.getErrorStream();
                    String err = "";
                    if (errStr != null) {
                        try (java.util.Scanner scanner = new java.util.Scanner(errStr).useDelimiter("\\A")) {
                            err = scanner.hasNext() ? scanner.next() : "";
                        }
                    }
                    final String finalErr = err;
                    runOnUiThread(() -> {
                        tvAiTestResult.setText("错误(" + code + "): " + finalErr);
                        tvAiTestResult.setTextColor(0xFFEF4444); // Red
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvAiTestResult.setText("连接失败: " + e.getMessage());
                    tvAiTestResult.setTextColor(0xFFEF4444);
                });
            }
        }).start();
    }

    private void uploadLatestManualBackup() {
        saveConfig();
        status("上传中...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException("云配置不完整");
                BackupResult latest = graph.useCases.getLatestBackup.execute("manual");
                if (latest == null || !latest.success || latest.filePath == null || latest.filePath.trim().isEmpty()) {
                    BackupResult created = graph.useCases.createBackup.execute("manual");
                    if (!created.success)
                        throw new IllegalStateException("创建备份失败: " + created.errorMessage);
                    latest = created;
                }
                File file = new File(latest.filePath);
                String uploaded = cloudSyncClient.uploadBackup(config, file, "manual");
                runOnUiThread(() -> status("上传成功: " + uploaded));
            } catch (Exception e) {
                runOnUiThread(() -> status("上传失败: " + e.getMessage()));
            }
        }).start();
    }

    private void listRemoteBackups() {
        saveConfig();
        status("获取列表中...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException("云配置不完整");
                List<String> lines = cloudSyncClient.listBackups(config, 50);
                runOnUiThread(() -> {
                    status("列表获取成功: " + lines.size() + " 条");
                    if (lines.isEmpty()) {
                        tvRemoteList.setText("暂无远端备份");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String line : lines)
                        sb.append(line).append('\n');
                    tvRemoteList.setText(sb.toString().trim());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status("列表获取失败: " + e.getMessage()));
            }
        }).start();
    }

    private void downloadAndRestore() {
        saveConfig();
        String filename = text(etDownloadFilename);
        if (filename.isEmpty()) {
            status("请输入文件名");
            return;
        }
        status("下载中...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException("云配置不完整");
                File cloudDir = new File(requireContext().getFilesDir(), "lifeos_backups/cloud");
                if (!cloudDir.exists() && !cloudDir.mkdirs())
                    throw new IllegalStateException("无法创建目录");
                File target = new File(cloudDir, Instant.now().toEpochMilli() + "_" + filename);
                CloudSyncClient.DownloadResult result = cloudSyncClient.downloadBackup(config, filename, target);
                BackupResult registered = graph.useCases.registerExternalBackup.execute(
                        result.file.getAbsolutePath(), "manual", result.sizeBytes, null);
                com.example.skyeos.domain.model.RestoreResult restore = graph.useCases.restoreBackup
                        .execute(registered.id);
                runOnUiThread(() -> status(restore.success ? "恢复成功 ✓" : "恢复失败: " + restore.errorMessage));
            } catch (Exception e) {
                runOnUiThread(() -> status("下载/恢复失败: " + e.getMessage()));
            }
        }).start();
    }

    private CloudSyncConfig collectConfig() {
        return new CloudSyncConfig(text(etServerUrl), text(etApiKey), text(etDeviceId));
    }

    private AiApiConfig collectAiConfig() {
        return new AiApiConfig(AiApiProvider.fromString(text(etAiProvider)), text(etAiBaseUrl),
                text(etAiApiKey), text(etAiModel), text(etAiSystemPrompt));
    }

    private void setupAiProviderDropdown() {
        String[] values = new String[] { "custom", "deepseek", "siliconflow" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                values);
        etAiProvider.setAdapter(adapter);
        etAiProvider.setOnItemClickListener((parent, view, position, id) -> {
            AiApiProvider provider = AiApiProvider.fromString(values[position]);
            if (text(etAiBaseUrl).isEmpty()) {
                etAiBaseUrl.setText(provider.defaultBaseUrl());
            }
            if (text(etAiModel).isEmpty()) {
                etAiModel.setText(provider.defaultModel());
            }
        });
    }

    private void setupTagScopeDropdown() {
        String[] values = new String[] { "global", "time", "project", "income", "expense", "learning", "investment" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                values);
        etTagScope.setAdapter(adapter);
    }

    private void createTag() {
        String name = text(etTagName);
        String emoji = text(etTagEmoji);
        String scope = text(etTagScope);
        if (name.isEmpty()) {
            status("标签名称不能为空");
            return;
        }
        try {
            graph.useCases.createTag.execute(name, emoji, "custom", scope.isEmpty() ? "global" : scope);
            etTagName.setText("");
            etTagEmoji.setText("");
            status("标签已创建");
            refreshTagList();
        } catch (Exception e) {
            status("标签创建失败: " + e.getMessage());
        }
    }

    private void refreshTagList() {
        try {
            List<TagItem> tags = graph.useCases.getTags.execute("all", false);
            if (tags == null || tags.isEmpty()) {
                tvTagList.setText("暂无标签");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (TagItem tag : tags) {
                if (tag == null) {
                    continue;
                }
                sb.append(tag.emoji == null || tag.emoji.isEmpty() ? "" : tag.emoji + " ")
                        .append(tag.name)
                        .append(" [").append(tag.scope).append("]")
                        .append(tag.isSystem ? " · 系统" : " · 自定义")
                        .append(tag.isActive ? "" : " · 已停用")
                        .append('\n');
            }
            tvTagList.setText(sb.toString().trim());
        } catch (Exception e) {
            tvTagList.setText("标签加载失败: " + e.getMessage());
        }
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String text(AutoCompleteTextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void status(String msg) {
        tvStatus.setText(msg);
    }

    private void runOnUiThread(Runnable r) {
        if (getActivity() != null)
            getActivity().runOnUiThread(r);
    }

    private static long parseYuanToCents(String yuanText) {
        if (yuanText == null || yuanText.trim().isEmpty()) {
            return 0L;
        }
        try {
            double value = Double.parseDouble(yuanText.trim());
            if (value < 0) {
                return 0L;
            }
            return Math.round(value * 100.0);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
