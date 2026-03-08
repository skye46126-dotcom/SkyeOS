package com.example.skyeos.cloud;

public final class CloudSyncConfig {
    public final String serverBaseUrl;
    public final String apiKey;
    public final String deviceId;

    public CloudSyncConfig(String serverBaseUrl, String apiKey, String deviceId) {
        this.serverBaseUrl = serverBaseUrl;
        this.apiKey = apiKey;
        this.deviceId = deviceId;
    }

    public boolean isValid() {
        return notBlank(serverBaseUrl) && notBlank(apiKey) && notBlank(deviceId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

