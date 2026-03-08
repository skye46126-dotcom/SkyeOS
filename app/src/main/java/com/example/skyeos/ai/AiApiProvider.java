package com.example.skyeos.ai;

import java.util.Locale;

public enum AiApiProvider {
    CUSTOM,
    DEEPSEEK,
    SILICONFLOW;

    public static AiApiProvider fromString(String raw) {
        if (raw == null) {
            return CUSTOM;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        switch (value) {
            case "deepseek":
                return DEEPSEEK;
            case "siliconflow":
                return SILICONFLOW;
            case "custom":
            default:
                return CUSTOM;
        }
    }

    public String toPersistedValue() {
        return name().toLowerCase(Locale.US);
    }

    public String defaultBaseUrl() {
        switch (this) {
            case DEEPSEEK:
                return "https://api.deepseek.com";
            case SILICONFLOW:
                return "https://api.siliconflow.cn/v1";
            case CUSTOM:
            default:
                return "";
        }
    }

    public String defaultModel() {
        switch (this) {
            case DEEPSEEK:
                return "deepseek-chat";
            case SILICONFLOW:
                return "deepseek-ai/DeepSeek-V3";
            case CUSTOM:
            default:
                return "gpt-4o-mini";
        }
    }
}
