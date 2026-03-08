package com.example.skyeos.cloud;

import android.content.Context;
import android.content.SharedPreferences;

public final class CloudSyncConfigStore {
    private static final String PREF = "lifeos_cloud_sync";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_DEVICE_ID = "device_id";

    private final SharedPreferences preferences;

    public CloudSyncConfigStore(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public CloudSyncConfig load() {
        return new CloudSyncConfig(
                preferences.getString(KEY_BASE_URL, ""),
                preferences.getString(KEY_API_KEY, ""),
                preferences.getString(KEY_DEVICE_ID, "android-self")
        );
    }

    public void save(CloudSyncConfig config) {
        preferences.edit()
                .putString(KEY_BASE_URL, safe(config.serverBaseUrl))
                .putString(KEY_API_KEY, safe(config.apiKey))
                .putString(KEY_DEVICE_ID, safe(config.deviceId))
                .apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

