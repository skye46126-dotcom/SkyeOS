package com.example.skyeos.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import javax.inject.Inject;

public final class AiApiConfigStore {
    private static final String PREF = "lifeos_ai_api_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";

    private final SharedPreferences sharedPreferences;

    @Inject
    public AiApiConfigStore(@dagger.hilt.android.qualifiers.ApplicationContext Context context) {
        Context appContext = context.getApplicationContext();
        this.sharedPreferences = createSecurePreferences(appContext);
    }

    public AiApiConfig load() {
        return new AiApiConfig(
                AiApiProvider.fromString(sharedPreferences.getString(KEY_PROVIDER, "custom")),
                sharedPreferences.getString(KEY_BASE_URL, ""),
                sharedPreferences.getString(KEY_API_KEY, ""),
                sharedPreferences.getString(KEY_MODEL, "gpt-4o-mini"),
                sharedPreferences.getString(KEY_SYSTEM_PROMPT, AiApiConfig.defaultSystemPrompt())
        );
    }

    public void save(AiApiConfig config) {
        if (config == null) {
            return;
        }
        sharedPreferences.edit()
                .putString(KEY_PROVIDER, config.provider.toPersistedValue())
                .putString(KEY_BASE_URL, safe(config.baseUrl))
                .putString(KEY_API_KEY, safe(config.apiKey))
                .putString(KEY_MODEL, safe(config.model))
                .putString(KEY_SYSTEM_PROMPT, safe(config.systemPrompt))
                .apply();
    }

    private static SharedPreferences createSecurePreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREF,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception ignored) {
            return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
