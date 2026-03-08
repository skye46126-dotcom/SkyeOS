package com.example.skyeos.ai;

public final class AiApiConfig {
    public final AiApiProvider provider;
    public final String baseUrl;
    public final String apiKey;
    public final String model;
    public final String systemPrompt;

    public AiApiConfig(AiApiProvider provider, String baseUrl, String apiKey, String model, String systemPrompt) {
        this.provider = provider == null ? AiApiProvider.CUSTOM : provider;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
    }

    public boolean isValid() {
        return !resolvedBaseUrl().isEmpty() && !apiKey.isEmpty() && !resolvedModel().isEmpty();
    }

    public String resolvedBaseUrl() {
        if (!baseUrl.isEmpty()) {
            return baseUrl;
        }
        return provider.defaultBaseUrl();
    }

    public String resolvedModel() {
        if (!model.isEmpty()) {
            return model;
        }
        return provider.defaultModel();
    }

    public String resolvedSystemPrompt() {
        if (!systemPrompt.isEmpty()) {
            return systemPrompt;
        }
        return defaultSystemPrompt();
    }

    public static String defaultSystemPrompt() {
        return "你是 LifeOS 的结构化解析器。"
                + "把用户自然语言拆解为 items。"
                + "只输出 JSON，不要 markdown，不要解释。"
                + "kind 只允许 time_log/income/expense/learning/unknown。"
                + "time_log payload 推荐字段: category,start_hour,end_hour,duration_hours,description。"
                + "income payload 推荐字段: source,type,amount。"
                + "expense payload 推荐字段: category,amount,note。"
                + "learning payload 推荐字段: content,duration_minutes,application_level。"
                + "若无法判断，使用 unknown 并写 warning。";
    }
}
