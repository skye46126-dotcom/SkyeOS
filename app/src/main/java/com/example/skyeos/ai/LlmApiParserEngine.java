package com.example.skyeos.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LlmApiParserEngine implements ParserEngine {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    private final AiApiConfigStore configStore;

    public LlmApiParserEngine(AiApiConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public ParseResult parse(String rawText, String contextDate) throws Exception {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ParseResult.empty("llm_api", "raw text is empty");
        }
        AiApiConfig config = configStore.load();
        if (config == null || !config.isValid()) {
            throw new IllegalStateException("AI API config is incomplete");
        }

        String endpoint = buildChatEndpoint(config.resolvedBaseUrl());
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONObject body = buildChatRequest(config.resolvedModel(), config.resolvedSystemPrompt(), rawText, contextDate);
        try (OutputStream out = conn.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(body.toString());
            writer.flush();
        }

        int code = conn.getResponseCode();
        String raw = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("LLM API failed (" + code + "): " + raw);
        }

        JSONObject response = new JSONObject(raw);
        String requestId = response.optString("id", UUID.randomUUID().toString());
        JSONObject parsed = extractParsedJson(response);
        return toParseResult(parsed, requestId);
    }

    private static JSONObject buildChatRequest(
            String model,
            String systemPrompt,
            String rawText,
            String contextDate
    ) throws JSONException {
        String schemaHint = "{"
                + "\"items\":["
                + "{\"kind\":\"time_log|income|expense|learning|unknown\","
                + "\"confidence\":0.0,"
                + "\"source\":\"llm_api\","
                + "\"warning\":\"\","
                + "\"payload\":{}}"
                + "],"
                + "\"warnings\":[]"
                + "}";

        String userPrompt = String.format(Locale.US,
                "context_date=%s\nraw_text=\n%s\n\n按此 JSON 结构输出：\n%s\n\npayload 可按类型附带字段：\n" +
                        "- time_log: category,start_hour,end_hour,duration_hours,description,ai_ratio(0-100),efficiency_score(1-10),value_score(1-10),state_score(1-10)\n" +
                        "- income: source,type,amount,ai_ratio(0-100)\n" +
                        "- expense: category,amount,note,ai_ratio(0-100)\n" +
                        "- learning: content,duration_minutes,application_level,ai_ratio(0-100),efficiency_score(1-10)\n" +
                        "注意：主观评分字段（efficiency_score/value_score/state_score）默认不要猜测，只有原文明确给分才提取。\n" +
                        "缺失字段可省略，不要编造。",
                contextDate == null ? "" : contextDate.trim(),
                rawText == null ? "" : rawText.trim(),
                schemaHint);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userPrompt));

        return new JSONObject()
                .put("model", model)
                .put("temperature", 0.1)
                .put("messages", messages);
    }

    private static ParseResult toParseResult(JSONObject parsed, String requestId) {
        List<ParseDraftItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JSONArray ws = parsed.optJSONArray("warnings");
        if (ws != null) {
            for (int i = 0; i < ws.length(); i++) {
                String w = ws.optString(i, "");
                if (w != null && !w.trim().isEmpty()) {
                    warnings.add(w.trim());
                }
            }
        }

        JSONArray arr = parsed.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it == null) {
                    continue;
                }
                String kind = it.optString("kind", "unknown").trim().toLowerCase(Locale.US);
                double confidence = it.optDouble("confidence", 0.5);
                String source = it.optString("source", "llm_api");
                String warning = it.optString("warning", "");
                Map<String, String> payload = toStringMap(it.optJSONObject("payload"));
                items.add(new ParseDraftItem(kind, payload, clamp01(confidence), source, warning));
            }
        }
        return new ParseResult(requestId, items, warnings, "llm_api");
    }

    private static Map<String, String> toStringMap(JSONObject payloadObj) {
        Map<String, String> map = new HashMap<>();
        if (payloadObj == null) {
            return map;
        }
        JSONArray names = payloadObj.names();
        if (names == null) {
            return map;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            if (key.isEmpty()) {
                continue;
            }
            Object val = payloadObj.opt(key);
            map.put(key, val == null ? "" : String.valueOf(val));
        }
        return map;
    }

    private static JSONObject extractParsedJson(JSONObject response) throws JSONException {
        if (response.has("items")) {
            return response;
        }
        String content = extractAssistantContent(response);
        return toJsonObject(content);
    }

    private static String extractAssistantContent(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("LLM API response has no choices");
        }
        JSONObject first = choices.optJSONObject(0);
        if (first == null) {
            throw new IllegalStateException("LLM API first choice is invalid");
        }
        JSONObject message = first.optJSONObject("message");
        if (message == null) {
            throw new IllegalStateException("LLM API response has no message");
        }
        String content = readMessageContent(message);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("LLM API content is empty");
        }
        return content.trim();
    }

    private static String readMessageContent(JSONObject message) {
        Object contentObj = message.opt("content");
        if (contentObj instanceof String) {
            return ((String) contentObj).trim();
        }
        if (contentObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) contentObj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                Object part = arr.opt(i);
                if (part instanceof JSONObject) {
                    JSONObject partObj = (JSONObject) part;
                    String t = partObj.optString("text", "");
                    if (!t.isEmpty()) {
                        sb.append(t);
                    }
                } else if (part instanceof String) {
                    sb.append((String) part);
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private static JSONObject toJsonObject(String rawContent) throws JSONException {
        String text = rawContent == null ? "" : rawContent.trim();
        if (text.startsWith("```")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                text = text.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        }
        return new JSONObject(text);
    }

    private static String normalizeBaseUrl(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String buildChatEndpoint(String rawBaseUrl) {
        String base = normalizeBaseUrl(rawBaseUrl);
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static String readBody(InputStream inputStream) throws Exception {
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
}
