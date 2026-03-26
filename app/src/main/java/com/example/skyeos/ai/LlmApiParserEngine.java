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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LlmApiParserEngine implements ParserEngine {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern FULL_DATE_TOKEN = Pattern.compile("(\\d{4})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY_TOKEN = Pattern.compile("(\\d{1,2})\\s*[月/-]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern CLOCK_TOKEN = Pattern.compile(
            "(?i)(上午|早上|中午|下午|晚上|凌晨|傍晚|am|pm)?\\s*(\\d{1,2})(?:[:点时](\\d{1,2}))?(半)?\\s*(am|pm)?");
    private static final DateTimeFormatter[] LOCAL_DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    };

    private final AiApiConfigStore configStore;

    @javax.inject.Inject
    public LlmApiParserEngine(AiApiConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public ParseResult parse(String rawText, String contextDate, ParserContext context) throws Exception {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ParseResult.empty("llm_api", "raw text is empty");
        }
        AiApiConfig config = configStore.load();
        if (config == null || !config.isValid()) {
            throw new IllegalStateException("AI API config is incomplete");
        }

        String endpoint = buildChatEndpoint(config.resolvedBaseUrl());
        String normalizedContextDate = normalizeDate(contextDate, LocalDate.now().toString());
        boolean fallbackToLegacyMode = false;
        String raw;
        try {
            JSONObject strictBody = buildChatRequest(config.resolvedModel(), config.resolvedSystemPrompt(), rawText,
                    normalizedContextDate, context, true);
            raw = executeChatRequest(endpoint, config.apiKey, strictBody);
        } catch (IllegalStateException strictError) {
            if (!shouldRetryWithoutJsonMode(strictError.getMessage())) {
                throw strictError;
            }
            fallbackToLegacyMode = true;
            JSONObject legacyBody = buildChatRequest(config.resolvedModel(), config.resolvedSystemPrompt(), rawText,
                    normalizedContextDate, context, false);
            raw = executeChatRequest(endpoint, config.apiKey, legacyBody);
        }

        JSONObject response = new JSONObject(raw);
        String requestId = response.optString("id", UUID.randomUUID().toString());
        JSONObject parsed = extractParsedJson(response);
        ParseResult result = toParseResult(parsed, requestId, normalizedContextDate);
        if (!fallbackToLegacyMode) {
            return result;
        }
        List<String> warnings = new ArrayList<>(result.warnings);
        warnings.add("llm fallback: response_format=json_object is not supported by current provider");
        return new ParseResult(result.requestId, result.items, warnings, result.parserUsed);
    }

    private static JSONObject buildChatRequest(
            String model,
            String systemPrompt,
            String rawText,
            String contextDate,
            ParserContext context,
            boolean enforceJsonResponse) throws JSONException {
        String schemaHint = "{"
                + "\"items\":["
                + "{"
                + "\"kind\":\"time_log|income|expense|learning|unknown\","
                + "\"confidence\":0.0,"
                + "\"source\":\"llm_api\","
                + "\"warning\":\"\","
                + "\"payload\":{"
                + "\"date\":\"YYYY-MM-DD\","
                + "\"description\":\"\","
                + "\"category\":\"\","
                + "\"start_time\":\"HH:mm\","
                + "\"end_time\":\"HH:mm\","
                + "\"duration_minutes\":0,"
                + "\"source\":\"\","
                + "\"type\":\"\","
                + "\"amount\":0,"
                + "\"amount_cents\":0,"
                + "\"content\":\"\","
                + "\"application_level\":\"\","
                + "\"ai_ratio\":0,"
                + "\"efficiency_score\":0,"
                + "\"value_score\":0,"
                + "\"state_score\":0,"
                + "\"note\":\"\","
                + "\"project_names\":[],"
                + "\"tag_names\":[]"
                + "}"
                + "}"
                + "],"
                + "\"warnings\":[]"
                + "}";

        String technicalInstructions = "### 技术指令 ###\n"
                + "1. 只输出 JSON，不要 Markdown，不要解释。\n"
                + "2. kind 只允许: time_log, income, expense, learning, unknown。\n"
                + "3. 输入通常来自语音转写，请先拆分事件，再逐条结构化。\n"
                + "4. 每个 item 只表达一个事件，不要把多个事件塞到一个 payload。\n"
                + "5. date 用 YYYY-MM-DD；若原文未给日期，默认使用 context_date。\n"
                + "6. time_log.category 只能取: work, learning, life, entertainment, rest, social。\n"
                + "7. income.type 只能取: salary, project, investment, system, other。\n"
                + "8. expense.category 只能取: necessary, experience, subscription, investment。\n"
                + "9. learning.application_level 只能取: input, applied, result。\n"
                + "10. 时间字段优先填 start_time/end_time(HH:mm)；若只有时长则填 duration_minutes。\n"
                + "11. amount 默认表示人民币“元”；若明确是“分”，写 amount_cents。\n"
                + "12. ai_ratio 0-100；efficiency/value/state score 1-10；无明确信息就省略。\n"
                + "13. source 固定填 llm_api；confidence 填 0-1 小数。\n"
                + "14. 无法识别的句子用 kind=unknown，并在 payload.raw 保留原文。\n"
                + "15. context_date=" + contextDate + ", timezone=Asia/Shanghai。\n"
                + buildContextHint(context)
                + "\n### 待处理文本 ###\n"
                + (rawText == null ? "" : rawText.trim())
                + "\n\n### 输出 JSON 结构 ###\n"
                + schemaHint
                + "\n\n### 字段说明 ###\n"
                + "- time_log: date, category, start_time, end_time, duration_minutes, description, ai_ratio, efficiency_score, value_score, state_score, project_names[], tag_names[]\n"
                + "- income: date, source, type, amount|amount_cents, is_passive, ai_ratio, note, project_names[], tag_names[]\n"
                + "- expense: date, category, amount|amount_cents, note, ai_ratio, project_names[], tag_names[]\n"
                + "- learning: date, content, start_time, end_time, duration_minutes, application_level, ai_ratio, efficiency_score, note, project_names[], tag_names[]\n"
                + "\n### 约束条件 ###\n"
                + "1. 优先匹配提供的可用分类/项目/标签。\n"
                + "2. 如果规则引擎已提取部分内容，请验证并完善它。\n"
                + "3. 缺失字段可省略，不要编造。\n"
                + "4. 如果一句话里有“然后/接着/后来”等连接词，通常代表多条 item。";

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", technicalInstructions));

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("temperature", 0.1)
                .put("messages", messages);
        if (enforceJsonResponse) {
            body.put("response_format", new JSONObject().put("type", "json_object"));
        }
        return body;
    }

    private static ParseResult toParseResult(JSONObject parsed, String requestId, String contextDate) {
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
                String kind = normalizeKind(it.optString("kind", "unknown"));
                double confidence = normalizeConfidence(it.opt("confidence"));
                String source = it.optString("source", "llm_api");
                String warning = it.optString("warning", "");
                Map<String, String> rawPayload = toStringMap(it.opt("payload"));
                Map<String, String> payload = normalizePayload(kind, rawPayload, contextDate);
                items.add(new ParseDraftItem(kind, payload, clamp01(confidence), source, warning));
            }
        }
        return new ParseResult(requestId, items, warnings, "llm_api");
    }

    private static Map<String, String> toStringMap(Object payloadValue) {
        Map<String, String> map = new HashMap<>();
        if (payloadValue == null) {
            return map;
        }
        JSONObject payloadObj = null;
        if (payloadValue instanceof JSONObject) {
            payloadObj = (JSONObject) payloadValue;
        } else if (payloadValue instanceof String) {
            String raw = ((String) payloadValue).trim();
            if (raw.startsWith("{") && raw.endsWith("}")) {
                try {
                    payloadObj = new JSONObject(raw);
                } catch (Exception ignored) {
                    map.put("raw", raw);
                    return map;
                }
            } else if (!raw.isEmpty()) {
                map.put("raw", raw);
                return map;
            }
        } else {
            map.put("raw", String.valueOf(payloadValue));
            return map;
        }
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
            if (val instanceof JSONArray || val instanceof JSONObject) {
                map.put(key, val.toString());
            } else {
                map.put(key, val == null ? "" : String.valueOf(val));
            }
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

    private static String executeChatRequest(String endpoint, String apiKey, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

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
        return raw;
    }

    private static boolean shouldRetryWithoutJsonMode(String error) {
        String text = error == null ? "" : error.toLowerCase(Locale.US);
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("response_format")
                || text.contains("json_object")
                || text.contains("json mode")
                || text.contains("unsupported")
                || text.contains("invalid parameter");
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

    private static String buildContextHint(ParserContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!context.categories.isEmpty()) {
            builder.append("可用分类：").append(context.categories).append('\n');
        }
        if (!context.projectNames.isEmpty()) {
            builder.append("可用项目：").append(context.projectNames).append('\n');
        }
        if (!context.tagNames.isEmpty()) {
            builder.append("可用标签：").append(context.tagNames).append('\n');
        }
        if (!context.ruleHints.isEmpty()) {
            builder.append("规则引擎初步提取（供参考）：\n");
            for (ParseDraftItem hint : context.ruleHints) {
                if (hint == null) {
                    continue;
                }
                builder.append("- ").append(hint.kind).append(": ").append(hint.payload).append('\n');
            }
        }
        return builder.toString();
    }

    private static String normalizeKind(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (value.equals("time") || value.equals("timelog") || value.equals("time-log")) {
            return "time_log";
        }
        if (value.equals("income") || value.equals("expense") || value.equals("learning")
                || value.equals("time_log") || value.equals("unknown")) {
            return value;
        }
        if (value.contains("time") || value.contains("工作") || value.contains("时间")) {
            return "time_log";
        }
        if (value.contains("income") || value.contains("收入")) {
            return "income";
        }
        if (value.contains("expense") || value.contains("支出") || value.contains("消费")) {
            return "expense";
        }
        if (value.contains("learning") || value.contains("学习")) {
            return "learning";
        }
        return "unknown";
    }

    private static double normalizeConfidence(Object raw) {
        if (raw == null) {
            return 0.5;
        }
        Double value = parseDecimal(String.valueOf(raw));
        if (value == null) {
            return 0.5;
        }
        if (value > 1.0 && value <= 100.0) {
            return value / 100.0;
        }
        return value;
    }

    private static Map<String, String> normalizePayload(String kind, Map<String, String> raw, String contextDate) {
        switch (kind) {
            case "time_log":
                return normalizeTimePayload(raw, contextDate);
            case "income":
                return normalizeIncomePayload(raw, contextDate);
            case "expense":
                return normalizeExpensePayload(raw, contextDate);
            case "learning":
                return normalizeLearningPayload(raw, contextDate);
            case "unknown":
            default:
                return normalizeUnknownPayload(raw, contextDate);
        }
    }

    private static Map<String, String> normalizeTimePayload(Map<String, String> raw, String contextDate) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("date", normalizeDate(firstNonBlank(raw, "date", "occurred_on", "occurredOn"), contextDate));
        payload.put("category", normalizeTimeCategory(firstNonBlank(raw, "category", "time_category", "type")));
        putIfNotBlank(payload, "description", firstNonBlank(raw, "description", "note", "content", "raw"));
        putIfNotBlank(payload, "start_time", normalizeClockTime(
                firstNonBlank(raw, "start_time", "start", "start_at", "started_at", "start_hour")));
        putIfNotBlank(payload, "end_time", normalizeClockTime(
                firstNonBlank(raw, "end_time", "end", "end_at", "ended_at", "end_hour")));
        Integer durationMinutes = parseDurationMinutes(
                firstNonBlank(raw, "duration_minutes", "duration", "minutes", "duration_min"));
        if (durationMinutes == null) {
            Double durationHours = parseDecimal(firstNonBlank(raw, "duration_hours", "hours"));
            if (durationHours != null && durationHours > 0) {
                durationMinutes = Math.max(1, (int) Math.round(durationHours * 60.0));
            }
        }
        if (durationMinutes != null && durationMinutes > 0) {
            payload.put("duration_minutes", String.valueOf(durationMinutes));
        }
        copyNullableInteger(payload, "ai_ratio",
                parsePercentage(firstNonBlank(raw, "ai_ratio", "aiAssistRatio", "ai_assist_ratio")));
        copyNullableInteger(payload, "efficiency_score",
                parseScore(firstNonBlank(raw, "efficiency_score", "efficiency")));
        copyNullableInteger(payload, "value_score", parseScore(firstNonBlank(raw, "value_score", "value")));
        copyNullableInteger(payload, "state_score", parseScore(firstNonBlank(raw, "state_score", "state")));
        putIfNotBlank(payload, "project_names",
                normalizeList(firstNonBlank(raw, "project_names", "projects", "project_name", "project")));
        putIfNotBlank(payload, "tag_names", normalizeList(firstNonBlank(raw, "tag_names", "tags", "tag")));
        return payload;
    }

    private static Map<String, String> normalizeIncomePayload(Map<String, String> raw, String contextDate) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("date", normalizeDate(firstNonBlank(raw, "date", "occurred_on", "occurredOn"), contextDate));
        putIfNotBlank(payload, "source", firstNonBlank(raw, "source", "source_name", "from", "description"));
        payload.put("type", normalizeIncomeType(firstNonBlank(raw, "type", "income_type", "category")));
        String amountYuan = normalizeAmountYuan(firstNonBlank(raw, "amount", "amount_yuan", "money"),
                firstNonBlank(raw, "amount_cents"));
        putIfNotBlank(payload, "amount", amountYuan);
        Long amountCents = parsePositiveLong(firstNonBlank(raw, "amount_cents"));
        if (amountCents != null) {
            payload.put("amount_cents", String.valueOf(amountCents));
        }
        copyNullableInteger(payload, "ai_ratio",
                parsePercentage(firstNonBlank(raw, "ai_ratio", "aiAssistRatio", "ai_assist_ratio")));
        putIfNotBlank(payload, "is_passive", parseBoolean(firstNonBlank(raw, "is_passive", "passive")) ? "true" : "");
        putIfNotBlank(payload, "note", firstNonBlank(raw, "note", "description"));
        putIfNotBlank(payload, "project_names",
                normalizeList(firstNonBlank(raw, "project_names", "projects", "project_name", "project")));
        putIfNotBlank(payload, "tag_names", normalizeList(firstNonBlank(raw, "tag_names", "tags", "tag")));
        return payload;
    }

    private static Map<String, String> normalizeExpensePayload(Map<String, String> raw, String contextDate) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("date", normalizeDate(firstNonBlank(raw, "date", "occurred_on", "occurredOn"), contextDate));
        payload.put("category", normalizeExpenseCategory(firstNonBlank(raw, "category", "expense_category", "type")));
        String amountYuan = normalizeAmountYuan(firstNonBlank(raw, "amount", "amount_yuan", "money"),
                firstNonBlank(raw, "amount_cents"));
        putIfNotBlank(payload, "amount", amountYuan);
        Long amountCents = parsePositiveLong(firstNonBlank(raw, "amount_cents"));
        if (amountCents != null) {
            payload.put("amount_cents", String.valueOf(amountCents));
        }
        copyNullableInteger(payload, "ai_ratio",
                parsePercentage(firstNonBlank(raw, "ai_ratio", "aiAssistRatio", "ai_assist_ratio")));
        putIfNotBlank(payload, "note", firstNonBlank(raw, "note", "description", "content"));
        putIfNotBlank(payload, "project_names",
                normalizeList(firstNonBlank(raw, "project_names", "projects", "project_name", "project")));
        putIfNotBlank(payload, "tag_names", normalizeList(firstNonBlank(raw, "tag_names", "tags", "tag")));
        return payload;
    }

    private static Map<String, String> normalizeLearningPayload(Map<String, String> raw, String contextDate) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("date", normalizeDate(firstNonBlank(raw, "date", "occurred_on", "occurredOn"), contextDate));
        putIfNotBlank(payload, "content", firstNonBlank(raw, "content", "description", "note", "raw"));
        putIfNotBlank(payload, "start_time", normalizeClockTime(
                firstNonBlank(raw, "start_time", "start", "started_at", "start_hour")));
        putIfNotBlank(payload, "end_time", normalizeClockTime(
                firstNonBlank(raw, "end_time", "end", "ended_at", "end_hour")));
        Integer durationMinutes = parseDurationMinutes(
                firstNonBlank(raw, "duration_minutes", "duration", "minutes", "duration_min"));
        if (durationMinutes == null) {
            Double durationHours = parseDecimal(firstNonBlank(raw, "duration_hours", "hours"));
            if (durationHours != null && durationHours > 0) {
                durationMinutes = Math.max(1, (int) Math.round(durationHours * 60.0));
            }
        }
        if (durationMinutes != null && durationMinutes > 0) {
            payload.put("duration_minutes", String.valueOf(durationMinutes));
        }
        payload.put("application_level",
                normalizeLearningLevel(firstNonBlank(raw, "application_level", "level", "learning_level")));
        copyNullableInteger(payload, "ai_ratio",
                parsePercentage(firstNonBlank(raw, "ai_ratio", "aiAssistRatio", "ai_assist_ratio")));
        copyNullableInteger(payload, "efficiency_score",
                parseScore(firstNonBlank(raw, "efficiency_score", "efficiency")));
        putIfNotBlank(payload, "note", firstNonBlank(raw, "note", "description"));
        putIfNotBlank(payload, "project_names",
                normalizeList(firstNonBlank(raw, "project_names", "projects", "project_name", "project")));
        putIfNotBlank(payload, "tag_names", normalizeList(firstNonBlank(raw, "tag_names", "tags", "tag")));
        return payload;
    }

    private static Map<String, String> normalizeUnknownPayload(Map<String, String> raw, String contextDate) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("date", normalizeDate(firstNonBlank(raw, "date", "occurred_on", "occurredOn"), contextDate));
        putIfNotBlank(payload, "raw", firstNonBlank(raw, "raw", "content", "description", "note"));
        if (payload.size() == 1) {
            putIfNotBlank(payload, "raw", raw == null ? "" : String.valueOf(raw));
        }
        return payload;
    }

    private static void copyNullableInteger(Map<String, String> payload, String key, Integer value) {
        if (payload == null || key == null || key.trim().isEmpty() || value == null) {
            return;
        }
        payload.put(key, String.valueOf(value));
    }

    private static void putIfNotBlank(Map<String, String> payload, String key, String value) {
        if (payload == null || key == null || key.trim().isEmpty()) {
            return;
        }
        String v = value == null ? "" : value.trim();
        if (!v.isEmpty()) {
            payload.put(key, v);
        }
    }

    private static String firstNonBlank(Map<String, String> payload, String... keys) {
        if (payload == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String value = payload.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeTimeCategory(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("learn") || text.contains("学习") || text.contains("阅读") || text.contains("课程")) {
            return "learning";
        }
        if (text.contains("life") || text.contains("生活") || text.contains("家务") || text.contains("通勤")) {
            return "life";
        }
        if (text.contains("entertain") || text.contains("娱乐") || text.contains("游戏") || text.contains("电影")) {
            return "entertainment";
        }
        if (text.contains("rest") || text.contains("休息") || text.contains("睡")) {
            return "rest";
        }
        if (text.contains("social") || text.contains("社交") || text.contains("朋友") || text.contains("聚会")) {
            return "social";
        }
        return "work";
    }

    private static String normalizeIncomeType(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("salary") || text.contains("工资") || text.contains("薪")) {
            return "salary";
        }
        if (text.contains("project") || text.contains("项目") || text.contains("外包")) {
            return "project";
        }
        if (text.contains("invest") || text.contains("投资") || text.contains("分红")) {
            return "investment";
        }
        if (text.contains("system") || text.contains("系统") || text.contains("补贴")) {
            return "system";
        }
        return "other";
    }

    private static String normalizeExpenseCategory(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("subscription") || text.contains("订阅") || text.contains("会员")) {
            return "subscription";
        }
        if (text.contains("invest") || text.contains("投资")) {
            return "investment";
        }
        if (text.contains("experience") || text.contains("体验") || text.contains("娱乐")
                || text.contains("旅游") || text.contains("聚餐")) {
            return "experience";
        }
        return "necessary";
    }

    private static String normalizeLearningLevel(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (text.contains("result") || text.contains("产出") || text.contains("成果")) {
            return "result";
        }
        if (text.contains("apply") || text.contains("applied") || text.contains("实践") || text.contains("应用")
                || text.contains("落地")) {
            return "applied";
        }
        return "input";
    }

    private static Integer parseScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseDecimal(raw);
        if (number == null) {
            return null;
        }
        int score = (int) Math.round(number);
        if (score < 1 || score > 10) {
            return null;
        }
        return score;
    }

    private static Integer parsePercentage(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseDecimal(raw);
        if (number == null) {
            return null;
        }
        if (number > 0 && number <= 1 && raw.contains(".")) {
            number = number * 100.0;
        }
        int value = (int) Math.round(number);
        if (value < 0 || value > 100) {
            return null;
        }
        return value;
    }

    private static Integer parseDurationMinutes(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseDecimal(raw);
        if (number == null || number <= 0) {
            return null;
        }
        String text = raw.toLowerCase(Locale.US);
        if (text.contains("小时") || text.contains("hour") || text.matches(".*\\bh\\b.*")) {
            return Math.max(1, (int) Math.round(number * 60.0));
        }
        if (text.contains("分钟") || text.contains("min") || text.matches(".*\\bm\\b.*")) {
            return Math.max(1, (int) Math.round(number));
        }
        if (text.contains(".") && number <= 12) {
            return Math.max(1, (int) Math.round(number * 60.0));
        }
        return Math.max(1, (int) Math.round(number));
    }

    private static String normalizeAmountYuan(String amountRaw, String amountCentsRaw) {
        Long cents = parsePositiveLong(amountCentsRaw);
        if (cents != null) {
            BigDecimal yuan = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return yuan.stripTrailingZeros().toPlainString();
        }
        if (amountRaw == null || amountRaw.trim().isEmpty()) {
            return "";
        }
        Double number = parseDecimal(amountRaw);
        if (number == null) {
            return "";
        }
        String text = amountRaw.toLowerCase(Locale.US);
        BigDecimal yuan = BigDecimal.valueOf(number);
        if (text.contains("万") || text.matches(".*\\bw\\b.*")) {
            yuan = yuan.multiply(BigDecimal.valueOf(10_000L));
        } else if (text.contains("千") || text.matches(".*\\bk\\b.*")) {
            yuan = yuan.multiply(BigDecimal.valueOf(1_000L));
        } else if (text.contains("分") && !text.contains("元") && !text.contains("块")) {
            yuan = yuan.divide(BigDecimal.valueOf(100L), 4, RoundingMode.HALF_UP);
        }
        return yuan.stripTrailingZeros().toPlainString();
    }

    private static Long parsePositiveLong(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Double number = parseDecimal(raw);
        if (number == null || number < 0) {
            return null;
        }
        return Math.round(number);
    }

    private static Double parseDecimal(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = NUMBER_TOKEN.matcher(raw.replace(",", "").replace("，", ""));
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeList(String raw) {
        List<String> values = parseList(raw);
        if (values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private static List<String> parseList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String text = raw.trim();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JSONArray arr = new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    String token = arr.optString(i, "").trim();
                    if (!token.isEmpty()) {
                        out.add(cleanToken(token));
                    }
                }
                return new ArrayList<>(out);
            } catch (Exception ignored) {
            }
        }
        String[] chunks = text.split("[,，、;；\\n|]");
        for (String chunk : chunks) {
            String token = cleanToken(chunk);
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return new ArrayList<>(out);
    }

    private static String cleanToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        while (token.startsWith("\"") || token.startsWith("'")) {
            token = token.substring(1).trim();
        }
        while (token.endsWith("\"") || token.endsWith("'")) {
            token = token.substring(0, token.length() - 1).trim();
        }
        return token;
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        String text = raw.trim().toLowerCase(Locale.US);
        return "true".equals(text)
                || "1".equals(text)
                || text.contains("是")
                || text.contains("被动")
                || text.contains("yes");
    }

    private static String normalizeDate(String raw, String fallback) {
        String fallbackDate = safeDate(fallback);
        if (raw == null || raw.trim().isEmpty()) {
            return fallbackDate;
        }
        String text = raw.trim();
        LocalDate anchor = LocalDate.parse(fallbackDate);
        if (text.contains("前天")) {
            return anchor.minusDays(2).toString();
        }
        if (text.contains("昨天")) {
            return anchor.minusDays(1).toString();
        }
        if (text.contains("明天")) {
            return anchor.plusDays(1).toString();
        }
        if (text.contains("后天")) {
            return anchor.plusDays(2).toString();
        }
        if (text.contains("今天")) {
            return anchor.toString();
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (Exception ignored) {
        }

        Matcher full = FULL_DATE_TOKEN.matcher(text);
        if (full.find()) {
            try {
                int y = Integer.parseInt(full.group(1));
                int m = Integer.parseInt(full.group(2));
                int d = Integer.parseInt(full.group(3));
                return LocalDate.of(y, m, d).toString();
            } catch (Exception ignored) {
            }
        }

        Matcher monthDay = MONTH_DAY_TOKEN.matcher(text);
        if (monthDay.find()) {
            try {
                int m = Integer.parseInt(monthDay.group(1));
                int d = Integer.parseInt(monthDay.group(2));
                return LocalDate.of(anchor.getYear(), m, d).toString();
            } catch (Exception ignored) {
            }
        }
        return fallbackDate;
    }

    private static String normalizeClockTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String text = raw.trim();
        try {
            return Instant.parse(text).atZone(SHANGHAI_ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(SHANGHAI_ZONE).toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(text, formatter);
                return dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception ignored) {
            }
        }
        Matcher matcher = CLOCK_TOKEN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String period = safeText(matcher.group(1));
        int hour;
        try {
            hour = Integer.parseInt(safeText(matcher.group(2)));
        } catch (Exception ignored) {
            return "";
        }
        int minute = 0;
        String minuteRaw = safeText(matcher.group(3));
        if (!minuteRaw.isEmpty()) {
            try {
                minute = Integer.parseInt(minuteRaw);
            } catch (Exception ignored) {
                minute = 0;
            }
        } else if (!safeText(matcher.group(4)).isEmpty()) {
            minute = 30;
        }
        String suffix = safeText(matcher.group(5));
        if (!suffix.isEmpty()) {
            period = suffix;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "";
        }
        hour = normalizeHourByPeriod(hour, period);
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private static int normalizeHourByPeriod(int hour, String periodRaw) {
        String period = periodRaw == null ? "" : periodRaw.trim().toLowerCase(Locale.US);
        if (period.isEmpty()) {
            return hour;
        }
        if (period.contains("pm") || period.contains("下午") || period.contains("晚上") || period.contains("傍晚")) {
            if (hour < 12) {
                return hour + 12;
            }
            return hour;
        }
        if (period.contains("am") || period.contains("早上") || period.contains("上午") || period.contains("凌晨")) {
            if (hour == 12) {
                return 0;
            }
            return hour;
        }
        if (period.contains("中午")) {
            if (hour < 11) {
                return hour + 12;
            }
            return hour;
        }
        return hour;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeDate(String raw) {
        try {
            return LocalDate.parse(raw == null ? "" : raw.trim()).toString();
        } catch (Exception ignored) {
            return LocalDate.now().toString();
        }
    }
}
