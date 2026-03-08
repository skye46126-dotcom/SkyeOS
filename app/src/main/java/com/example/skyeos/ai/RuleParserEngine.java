package com.example.skyeos.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleParserEngine implements ParserEngine {
    private static final Pattern TIME_RANGE = Pattern.compile("(?i)\\b(\\d{1,2})\\s*[-~到]\\s*(\\d{1,2})\\b\\s*(.*)");
    private static final Pattern HOURS_DESC = Pattern.compile("(上午|下午|晚上|中午)?\\s*(\\d+(?:\\.\\d+)?)\\s*小时\\s*(.*)");
    private static final Pattern AMOUNT_TAIL = Pattern.compile("(.*?)(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern EFF_SCORE = Pattern.compile("(?:效率|效能)\\s*([1-9]|10)\\s*分");
    private static final Pattern VAL_SCORE = Pattern.compile("价值\\s*([1-9]|10)\\s*分");
    private static final Pattern STATE_SCORE = Pattern.compile("状态\\s*([1-9]|10)\\s*分");
    private static final Pattern AI_RATIO = Pattern.compile("AI(?:辅助|参与|占比|率)?\\s*([1-9]\\d?|100)\\s*%?");

    @Override
    public ParseResult parse(String rawText, String contextDate) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ParseResult.empty("rule", "empty input");
        }
        String[] lines = rawText.split("\\r?\\n");
        List<ParseDraftItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String line : lines) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.startsWith("-")) {
                normalized = normalized.substring(1).trim();
            }
            ParseDraftItem parsed = tryParseTimeLog(normalized, contextDate);
            if (parsed == null) {
                parsed = tryParseIncome(normalized, contextDate);
            }
            if (parsed == null) {
                parsed = tryParseExpense(normalized, contextDate);
            }
            if (parsed == null) {
                parsed = tryParseLearning(normalized, contextDate);
            }
            if (parsed == null) {
                Map<String, String> unknownPayload = new HashMap<>();
                unknownPayload.put("raw", normalized);
                parsed = new ParseDraftItem("unknown", unknownPayload, 0.2, "rule", "unrecognized");
                warnings.add("unrecognized line: " + normalized);
            }
            items.add(parsed);
        }
        return new ParseResult(null, items, warnings, "rule");
    }

    private ParseDraftItem tryParseTimeLog(String line, String contextDate) {
        Matcher range = TIME_RANGE.matcher(line);
        if (range.matches()) {
            String startHour = range.group(1);
            String endHour = range.group(2);
            String desc = safe(range.group(3));
            Map<String, String> payload = new HashMap<>();
            payload.put("date", safe(contextDate));
            payload.put("start_hour", startHour);
            payload.put("end_hour", endHour);
            payload.put("description", desc);
            payload.put("category", inferCategory(desc));
            attachOptionalScoresAndAiRatio(payload, line);
            return new ParseDraftItem("time_log", payload, 0.8, "rule", null);
        }
        Matcher hours = HOURS_DESC.matcher(line);
        if (hours.matches()) {
            String durationHours = hours.group(2);
            String desc = safe(hours.group(3));
            Map<String, String> payload = new HashMap<>();
            payload.put("date", safe(contextDate));
            payload.put("duration_hours", durationHours);
            payload.put("description", desc);
            payload.put("category", inferCategory(desc));
            attachOptionalScoresAndAiRatio(payload, line);
            return new ParseDraftItem("time_log", payload, 0.7, "rule", "no explicit time range");
        }
        return null;
    }

    private ParseDraftItem tryParseIncome(String line, String contextDate) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!(lower.contains("收入") || lower.contains("工资") || lower.contains("项目") || lower.contains("到账"))) {
            return null;
        }
        Matcher amountMatcher = AMOUNT_TAIL.matcher(line);
        if (!amountMatcher.matches()) {
            return null;
        }
        String source = safe(amountMatcher.group(1));
        String amount = safe(amountMatcher.group(2));
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(contextDate));
        payload.put("source", source);
        payload.put("amount", amount);
        payload.put("type", inferIncomeType(source));
        attachOptionalAiRatio(payload, line);
        return new ParseDraftItem("income", payload, 0.75, "rule", null);
    }

    private ParseDraftItem tryParseExpense(String line, String contextDate) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!(lower.contains("花") || lower.contains("支出") || lower.contains("消费") || lower.contains("买"))) {
            return null;
        }
        Matcher amountMatcher = AMOUNT_TAIL.matcher(line);
        if (!amountMatcher.matches()) {
            return null;
        }
        String note = safe(amountMatcher.group(1));
        String amount = safe(amountMatcher.group(2));
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(contextDate));
        payload.put("amount", amount);
        payload.put("category", inferExpenseCategory(note));
        payload.put("note", note);
        attachOptionalAiRatio(payload, line);
        return new ParseDraftItem("expense", payload, 0.75, "rule", null);
    }

    private ParseDraftItem tryParseLearning(String line, String contextDate) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!(lower.contains("学习") || lower.contains("复习") || lower.contains("阅读") || lower.contains("课程"))) {
            return null;
        }
        Matcher hours = HOURS_DESC.matcher(line);
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(contextDate));
        payload.put("content", line);
        payload.put("application_level", "input");
        attachOptionalScoresAndAiRatio(payload, line);
        if (hours.matches()) {
            String duration = safe(hours.group(2));
            payload.put("duration_minutes", String.valueOf((int) (Double.parseDouble(duration) * 60)));
            payload.put("content", safe(hours.group(3)));
            return new ParseDraftItem("learning", payload, 0.8, "rule", null);
        }
        payload.put("duration_minutes", "60");
        return new ParseDraftItem("learning", payload, 0.6, "rule", "defaulted duration to 60m");
    }

    private static String inferCategory(String desc) {
        String lower = safe(desc).toLowerCase(Locale.ROOT);
        if (lower.contains("学习") || lower.contains("阅读") || lower.contains("课程")) {
            return "learning";
        }
        if (lower.contains("休息") || lower.contains("睡")) {
            return "rest";
        }
        if (lower.contains("娱乐") || lower.contains("电影") || lower.contains("游戏")) {
            return "entertainment";
        }
        if (lower.contains("社交") || lower.contains("朋友") || lower.contains("聚")) {
            return "social";
        }
        if (lower.contains("家务") || lower.contains("生活")) {
            return "life";
        }
        return "work";
    }

    private static String inferIncomeType(String source) {
        String lower = safe(source).toLowerCase(Locale.ROOT);
        if (lower.contains("工资")) {
            return "salary";
        }
        if (lower.contains("项目")) {
            return "project";
        }
        if (lower.contains("投资")) {
            return "investment";
        }
        return "other";
    }

    private static String inferExpenseCategory(String note) {
        String lower = safe(note).toLowerCase(Locale.ROOT);
        if (lower.contains("咖啡") || lower.contains("吃") || lower.contains("玩")) {
            return "experience";
        }
        if (lower.contains("会员") || lower.contains("订阅")) {
            return "subscription";
        }
        if (lower.contains("投资")) {
            return "investment";
        }
        return "necessary";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void attachOptionalScoresAndAiRatio(Map<String, String> payload, String line) {
        if (payload == null) {
            return;
        }
        String text = safe(line);
        Matcher eff = EFF_SCORE.matcher(text);
        if (eff.find()) {
            payload.put("efficiency_score", eff.group(1));
        }
        Matcher val = VAL_SCORE.matcher(text);
        if (val.find()) {
            payload.put("value_score", val.group(1));
        }
        Matcher state = STATE_SCORE.matcher(text);
        if (state.find()) {
            payload.put("state_score", state.group(1));
        }
        attachOptionalAiRatio(payload, text);
    }

    private static void attachOptionalAiRatio(Map<String, String> payload, String line) {
        if (payload == null) {
            return;
        }
        Matcher ai = AI_RATIO.matcher(safe(line));
        if (ai.find()) {
            payload.put("ai_ratio", ai.group(1));
        }
    }
}
