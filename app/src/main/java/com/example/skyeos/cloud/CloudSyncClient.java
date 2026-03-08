package com.example.skyeos.cloud;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CloudSyncClient {
    public String uploadBackup(CloudSyncConfig config, File backupFile, String backupType) throws IOException {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("Cloud config is invalid");
        }
        if (backupFile == null || !backupFile.exists()) {
            throw new IllegalArgumentException("Backup file does not exist");
        }

        String url = normalizeBaseUrl(config.serverBaseUrl)
                + "/api/v1/backups/upload?device_id="
                + enc(config.deviceId)
                + "&backup_type="
                + enc(backupType);
        String boundary = "----LifeOsBoundary" + UUID.randomUUID();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        conn.setRequestProperty("x-api-key", config.apiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream());
             FileInputStream fileInputStream = new FileInputStream(backupFile);
             BufferedInputStream fileIn = new BufferedInputStream(fileInputStream)) {
            writeLine(out, "--" + boundary);
            writeLine(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + backupFile.getName() + "\"");
            writeLine(out, "Content-Type: application/octet-stream");
            writeLine(out, "");

            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            writeLine(out, "--" + boundary + "--");
            out.flush();
        }

        int code = conn.getResponseCode();
        String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException("upload failed (" + code + "): " + body);
        }
        try {
            JSONObject obj = new JSONObject(body);
            return obj.optString("filename", "");
        } catch (JSONException e) {
            throw new IOException("invalid upload response json", e);
        }
    }

    public List<String> listBackups(CloudSyncConfig config, int limit) throws IOException {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("Cloud config is invalid");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String url = normalizeBaseUrl(config.serverBaseUrl) + "/api/v1/backups/list?limit=" + safeLimit;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("x-api-key", config.apiKey);

        int code = conn.getResponseCode();
        String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException("list failed (" + code + "): " + body);
        }

        try {
            JSONArray array = new JSONArray(body);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String filename = item.optString("filename", "-");
                long size = item.optLong("size_bytes", 0L);
                String modified = item.optString("modified_at", "-");
                result.add(modified + " | " + filename + " | " + size + " bytes");
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("invalid list response json", e);
        }
    }

    public DownloadResult downloadBackup(CloudSyncConfig config, String filename, File targetFile) throws IOException {
        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("Cloud config is invalid");
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("target file is required");
        }
        String url = normalizeBaseUrl(config.serverBaseUrl) + "/api/v1/backups/download/" + enc(filename);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(180000);
        conn.setRequestProperty("x-api-key", config.apiKey);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String body = readBody(conn.getErrorStream());
            throw new IOException("download failed (" + code + "): " + body);
        }

        long size = 0L;
        try (InputStream in = conn.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(in);
             FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bout = new BufferedOutputStream(fos)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bin.read(buffer)) != -1) {
                bout.write(buffer, 0, read);
                size += read;
            }
            bout.flush();
        }
        return new DownloadResult(targetFile, size);
    }

    private static String normalizeBaseUrl(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void writeLine(BufferedOutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(InputStream inputStream) throws IOException {
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

    public static final class DownloadResult {
        public final File file;
        public final long sizeBytes;

        public DownloadResult(File file, long sizeBytes) {
            this.file = file;
            this.sizeBytes = sizeBytes;
        }
    }
}
