package com.example.skyeos;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.skyeos.cloud.CloudSyncClient;
import com.example.skyeos.cloud.CloudSyncConfig;
import com.example.skyeos.cloud.CloudSyncConfigStore;
import com.example.skyeos.domain.model.BackupResult;
import com.example.skyeos.domain.model.RestoreResult;

import java.io.File;
import java.time.Instant;
import java.util.List;

public class CloudSyncActivity extends AppCompatActivity {
    private AppGraph graph;
    private CloudSyncConfigStore configStore;
    private CloudSyncClient cloudSyncClient;

    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private EditText deviceIdInput;
    private EditText downloadFilenameInput;
    private TextView statusView;
    private TextView remoteListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.graph = AppGraph.getInstance(this);
        this.configStore = new CloudSyncConfigStore(this);
        this.cloudSyncClient = new CloudSyncClient();
        setContentView(buildContentView());
        bindSavedConfig();
    }

    private ScrollView buildContentView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 40);

        TextView title = new TextView(this);
        title.setText("LifeOS Cloud Sync");
        title.setTextSize(22);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setPadding(0, 12, 0, 12);
        statusView.setText("Status: ready");
        root.addView(statusView);

        root.addView(fullButton("Back To Dashboard", v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));

        baseUrlInput = input("Server Base URL (e.g. https://your-domain.com)");
        apiKeyInput = input("API Key");
        deviceIdInput = input("Device ID (e.g. android-self)");
        root.addView(baseUrlInput);
        root.addView(apiKeyInput);
        root.addView(deviceIdInput);

        root.addView(fullButton("Save Cloud Config", v -> saveConfig()));
        root.addView(fullButton("Upload Latest Manual Backup", v -> uploadLatestManualBackup()));
        root.addView(fullButton("List Remote Backups", v -> listRemoteBackups()));
        downloadFilenameInput = input("Filename to download and restore");
        root.addView(downloadFilenameInput);
        root.addView(fullButton("Download & Restore By Filename", v -> downloadAndRestore()));

        remoteListView = new TextView(this);
        remoteListView.setPadding(0, 14, 0, 24);
        root.addView(remoteListView);

        scroll.addView(root);
        return scroll;
    }

    private void bindSavedConfig() {
        CloudSyncConfig config = configStore.load();
        baseUrlInput.setText(config.serverBaseUrl);
        apiKeyInput.setText(config.apiKey);
        deviceIdInput.setText(config.deviceId);
    }

    private void saveConfig() {
        CloudSyncConfig config = collectConfig();
        configStore.save(config);
        status("config saved");
    }

    private void uploadLatestManualBackup() {
        saveConfig();
        status("uploading...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid()) {
                    throw new IllegalStateException("cloud config invalid");
                }
                BackupResult latest = graph.useCases.getLatestBackup.execute("manual");
                if (latest == null || !latest.success || latest.filePath == null || latest.filePath.trim().isEmpty()) {
                    BackupResult created = graph.useCases.createBackup.execute("manual");
                    if (!created.success) {
                        throw new IllegalStateException("create backup failed: " + created.errorMessage);
                    }
                    latest = created;
                }
                File file = new File(latest.filePath);
                String uploadedFile = cloudSyncClient.uploadBackup(config, file, "manual");
                runOnUiThread(() -> status("upload success: " + uploadedFile));
            } catch (Exception e) {
                runOnUiThread(() -> status("upload failed: " + e.getMessage()));
            }
        }).start();
    }

    private void listRemoteBackups() {
        saveConfig();
        status("listing...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid()) {
                    throw new IllegalStateException("cloud config invalid");
                }
                List<String> lines = cloudSyncClient.listBackups(config, 50);
                runOnUiThread(() -> {
                    status("list success: " + lines.size());
                    if (lines.isEmpty()) {
                        remoteListView.setText("-");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String line : lines) {
                        sb.append(line).append('\n');
                    }
                    remoteListView.setText(sb.toString());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status("list failed: " + e.getMessage()));
            }
        }).start();
    }

    private void downloadAndRestore() {
        saveConfig();
        String filename = value(downloadFilenameInput);
        if (filename.isEmpty()) {
            status("download failed: filename is required");
            return;
        }
        status("downloading...");
        new Thread(() -> {
            try {
                CloudSyncConfig config = configStore.load();
                if (!config.isValid()) {
                    throw new IllegalStateException("cloud config invalid");
                }
                File cloudDir = new File(getFilesDir(), "lifeos_backups/cloud");
                if (!cloudDir.exists() && !cloudDir.mkdirs()) {
                    throw new IllegalStateException("cannot create cloud backup dir");
                }
                File target = new File(cloudDir, Instant.now().toEpochMilli() + "_" + filename);
                CloudSyncClient.DownloadResult result = cloudSyncClient.downloadBackup(config, filename, target);
                BackupResult registered = graph.useCases.registerExternalBackup.execute(
                        result.file.getAbsolutePath(),
                        "manual",
                        result.sizeBytes,
                        null
                );
                RestoreResult restore = graph.useCases.restoreBackup.execute(registered.id);
                runOnUiThread(() -> {
                    if (restore.success) {
                        status("restore success: " + filename);
                    } else {
                        status("restore failed: " + restore.errorMessage);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status("download/restore failed: " + e.getMessage()));
            }
        }).start();
    }

    private CloudSyncConfig collectConfig() {
        return new CloudSyncConfig(
                value(baseUrlInput),
                value(apiKeyInput),
                value(deviceIdInput)
        );
    }

    private static String value(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 10;
        input.setLayoutParams(params);
        return input;
    }

    private Button fullButton(String text, android.view.View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 10;
        button.setLayoutParams(params);
        button.setOnClickListener(click);
        return button;
    }

    private void status(String text) {
        statusView.setText("Status: " + text);
    }
}
