package com.example.skyeos.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.skyeos.AppLocaleManager;
import com.example.skyeos.AppGraph;
import com.example.skyeos.MainActivity;
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
    private AutoCompleteTextView etAiProvider, etTagScope, etAppLanguage;
    private TextInputEditText etAiBaseUrl, etAiApiKey, etAiModel, etAiSystemPrompt;
    private TextInputEditText etTagName, etTagEmoji;
    private TextView tvStatus, tvRemoteList, tvAiTestResult;
    private LinearLayout layoutTagList;

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
        etAppLanguage = view.findViewById(R.id.et_app_language);
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
        layoutTagList = view.findViewById(R.id.layout_tag_list);

        // Load saved config
        CloudSyncConfig config = configStore.load();
        if (config.serverBaseUrl != null)
            etServerUrl.setText(config.serverBaseUrl);
        if (config.apiKey != null)
            etApiKey.setText(config.apiKey);
        if (config.deviceId != null)
            etDeviceId.setText(config.deviceId);
        AiApiConfig aiConfig = graph.aiApiConfigStore.load();
        setupLanguageDropdown();
        setupAiProviderDropdown();
        setupTagScopeDropdown();
        etAppLanguage.setText(displayLanguage(AppLocaleManager.loadLanguageTag(requireContext())), false);
        etAiProvider.setText(aiConfig.provider.toPersistedValue(), false);
        etAiBaseUrl.setText(aiConfig.baseUrl);
        etAiApiKey.setText(aiConfig.apiKey);
        etAiModel.setText(aiConfig.model);
        etAiSystemPrompt.setText(aiConfig.resolvedSystemPrompt());
        etTagScope.setText(getString(R.string.settings_scope_global), false);
        refreshTagList();

        MaterialButton btnSave = view.findViewById(R.id.btn_save_config);
        MaterialButton btnUpload = view.findViewById(R.id.btn_upload_backup);
        MaterialButton btnList = view.findViewById(R.id.btn_list_backups);
        MaterialButton btnDownload = view.findViewById(R.id.btn_download_restore);
        MaterialButton btnTestAi = view.findViewById(R.id.btn_test_ai);
        MaterialButton btnCreateTag = view.findViewById(R.id.btn_create_tag);
        MaterialButton btnOpenCostManagement = view.findViewById(R.id.btn_open_cost_management);

        btnSave.setOnClickListener(v -> saveConfig());
        btnUpload.setOnClickListener(v -> uploadLatestManualBackup());
        btnList.setOnClickListener(v -> listRemoteBackups());
        btnDownload.setOnClickListener(v -> downloadAndRestore());
        btnTestAi.setOnClickListener(v -> testAiConnection());
        btnCreateTag.setOnClickListener(v -> createTag());
        btnOpenCostManagement.setOnClickListener(v -> openCostManagement());
    }

    private void saveConfig() {
        CloudSyncConfig config = collectConfig();
        configStore.save(config);
        AiApiConfig aiConfig = collectAiConfig();
        graph.aiApiConfigStore.save(aiConfig);
        AppLocaleManager.saveLanguageTag(requireContext(), languageTagFromDisplay(text(etAppLanguage)));
        status(getString(R.string.settings_status_config_saved));
        if (getActivity() != null) {
            getActivity().recreate();
        }
    }

    private void testAiConnection() {
        AiApiConfig config = collectAiConfig();
        tvAiTestResult.setText(R.string.settings_ai_pinging);
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
                        tvAiTestResult.setText(getString(R.string.settings_ai_connected, duration));
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
                        tvAiTestResult.setText(getString(R.string.settings_ai_error, code, finalErr));
                        tvAiTestResult.setTextColor(0xFFEF4444); // Red
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvAiTestResult.setText(getString(R.string.settings_ai_connection_failed, e.getMessage()));
                    tvAiTestResult.setTextColor(0xFFEF4444);
                });
            }
        }).start();
    }

    private void uploadLatestManualBackup() {
        saveConfig();
        status(getString(R.string.settings_status_uploading));
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException(getString(R.string.settings_cloud_config_incomplete));
                BackupResult latest = graph.useCases.getLatestBackup.execute("manual");
                if (latest == null || !latest.success || latest.filePath == null || latest.filePath.trim().isEmpty()) {
                    BackupResult created = graph.useCases.createBackup.execute("manual");
                    if (!created.success)
                        throw new IllegalStateException(getString(R.string.settings_backup_create_failed, created.errorMessage));
                    latest = created;
                }
                File file = new File(latest.filePath);
                String uploaded = cloudSyncClient.uploadBackup(config, file, "manual");
                runOnUiThread(() -> status(getString(R.string.settings_status_upload_success, uploaded)));
            } catch (Exception e) {
                runOnUiThread(() -> status(getString(R.string.settings_status_upload_failed, e.getMessage())));
            }
        }).start();
    }

    private void listRemoteBackups() {
        saveConfig();
        status(getString(R.string.settings_status_fetching_backup_list));
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException(getString(R.string.settings_cloud_config_incomplete));
                List<String> lines = cloudSyncClient.listBackups(config, 50);
                runOnUiThread(() -> {
                    status(getString(R.string.settings_status_list_loaded, lines.size()));
                    if (lines.isEmpty()) {
                        tvRemoteList.setText(R.string.settings_no_remote_backups);
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String line : lines)
                        sb.append(line).append('\n');
                    tvRemoteList.setText(sb.toString().trim());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status(getString(R.string.settings_status_list_failed, e.getMessage())));
            }
        }).start();
    }

    private void downloadAndRestore() {
        saveConfig();
        String filename = text(etDownloadFilename);
        if (filename.isEmpty()) {
            status(getString(R.string.settings_status_filename_required));
            return;
        }
        status(getString(R.string.settings_status_downloading));
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid())
                    throw new IllegalStateException(getString(R.string.settings_cloud_config_incomplete));
                File cloudDir = new File(requireContext().getFilesDir(), "lifeos_backups/cloud");
                if (!cloudDir.exists() && !cloudDir.mkdirs())
                    throw new IllegalStateException(getString(R.string.settings_create_directory_failed));
                File target = new File(cloudDir, Instant.now().toEpochMilli() + "_" + filename);
                CloudSyncClient.DownloadResult result = cloudSyncClient.downloadBackup(config, filename, target);
                BackupResult registered = graph.useCases.registerExternalBackup.execute(
                        result.file.getAbsolutePath(), "manual", result.sizeBytes, null);
                com.example.skyeos.domain.model.RestoreResult restore = graph.useCases.restoreBackup
                        .execute(registered.id);
                runOnUiThread(() -> status(restore.success ? getString(R.string.settings_status_restore_success) : getString(R.string.settings_status_restore_failed, restore.errorMessage)));
            } catch (Exception e) {
                runOnUiThread(() -> status(getString(R.string.settings_status_download_restore_failed, e.getMessage())));
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

    private void setupLanguageDropdown() {
        String[] values = new String[] {
                getString(R.string.settings_language_system),
                getString(R.string.settings_language_zh_cn),
                getString(R.string.settings_language_en)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                values);
        etAppLanguage.setAdapter(adapter);
    }

    private void setupTagScopeDropdown() {
        String[] values = new String[] {
                getString(R.string.settings_scope_global),
                getString(R.string.settings_scope_time),
                getString(R.string.settings_scope_project),
                getString(R.string.settings_scope_income),
                getString(R.string.settings_scope_expense),
                getString(R.string.settings_scope_learning),
                getString(R.string.settings_scope_investment)
        };
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
            status(getString(R.string.settings_tag_name_required));
            return;
        }
        try {
            graph.useCases.createTag.execute(name, emoji, "custom", scope.isEmpty() ? getString(R.string.settings_scope_global) : scope);
            etTagName.setText("");
            etTagEmoji.setText("");
            status(getString(R.string.settings_tag_created));
            refreshTagList();
        } catch (Exception e) {
            status(getString(R.string.settings_tag_create_failed, e.getMessage()));
        }
    }

    private void refreshTagList() {
        try {
            List<TagItem> tags = graph.useCases.getTags.execute("all", false);
            layoutTagList.removeAllViews();
            if (tags == null || tags.isEmpty()) {
                TextView empty = new TextView(requireContext());
                empty.setText(R.string.settings_no_tags);
                layoutTagList.addView(empty);
                return;
            }
            for (TagItem tag : tags) {
                if (tag == null) {
                    continue;
                }
                layoutTagList.addView(buildTagRow(tag));
            }
        } catch (Exception e) {
            layoutTagList.removeAllViews();
            TextView error = new TextView(requireContext());
            error.setText(getString(R.string.settings_load_tags_failed, e.getMessage()));
            layoutTagList.addView(error);
        }
    }

    private View buildTagRow(TagItem tag) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, 12);
        TextView label = new TextView(requireContext());
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        label.setText(getString(
                R.string.settings_tag_label,
                tag.emoji == null || tag.emoji.isEmpty() ? "" : tag.emoji + " ",
                tag.name,
                tag.scope,
                tag.isActive ? "" : getString(R.string.settings_tag_disabled_suffix)));
        row.addView(label);
        com.google.android.material.button.MaterialButton edit = new com.google.android.material.button.MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        edit.setText(R.string.common_edit);
        edit.setOnClickListener(v -> showEditTagDialog(tag));
        row.addView(edit);
        com.google.android.material.button.MaterialButton delete = new com.google.android.material.button.MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        delete.setText(R.string.common_delete);
        delete.setOnClickListener(v -> {
            graph.useCases.deleteTag.execute(tag.id);
            refreshTagList();
        });
        row.addView(delete);
        return row;
    }

    private void showEditTagDialog(TagItem tag) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        EditText etName2 = new EditText(requireContext());
        etName2.setHint(R.string.form_project_name_hint);
        etName2.setText(tag.name);
        layout.addView(etName2);
        EditText etEmoji2 = new EditText(requireContext());
        etEmoji2.setHint(R.string.common_emoji);
        etEmoji2.setText(tag.emoji);
        layout.addView(etEmoji2);
        EditText etScope2 = new EditText(requireContext());
        etScope2.setHint(R.string.settings_scope_hint);
        etScope2.setText(tag.scope);
        layout.addView(etScope2);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_edit_tag_title)
                .setView(layout)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_save, (dialog, which) -> {
                    try {
                        graph.useCases.updateTag.execute(tag.id,
                                etName2.getText().toString().trim(),
                                etEmoji2.getText().toString().trim(),
                                tag.tagGroup,
                                etScope2.getText().toString().trim(),
                                tag.isActive);
                        refreshTagList();
                    } catch (Exception e) {
                        status(getString(R.string.settings_tag_update_failed, e.getMessage()));
                    }
                })
                .show();
    }

    private void openCostManagement() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openCostManagement();
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

    private String displayLanguage(String tag) {
        if ("zh-CN".equalsIgnoreCase(tag)) {
            return getString(R.string.settings_language_zh_cn);
        }
        if ("en".equalsIgnoreCase(tag)) {
            return getString(R.string.settings_language_en);
        }
        return getString(R.string.settings_language_system);
    }

    private String languageTagFromDisplay(String label) {
        if (getString(R.string.settings_language_zh_cn).equalsIgnoreCase(label)) {
            return "zh-CN";
        }
        if (getString(R.string.settings_language_en).equalsIgnoreCase(label)) {
            return "en";
        }
        return "";
    }

    private void runOnUiThread(Runnable r) {
        if (getActivity() != null)
            getActivity().runOnUiThread(r);
    }
}
