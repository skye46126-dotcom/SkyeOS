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
        return "你是一个专业的个人生活管理助手（LifeOS），负责将用户随意记录的内容转化为结构化的数据。"
                + "你的目标是准确识别用户的意图，并将其分类为：时间日志（time_log）、收入（income）、支出（expense）或学习（learning）。"
                + "请保持客观、严谨，不要过度推测用户未提及的信息，除非原文明确提到，否则不要提取评分字段。";
    }
}
