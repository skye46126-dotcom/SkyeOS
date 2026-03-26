package com.example.skyeos.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleParserEngine implements ParserEngine {

    @javax.inject.Inject
    public RuleParserEngine() {
    }

    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?i)(上午|早上|中午|下午|晚上|凌晨|傍晚)?\\s*(\\d{1,2})\\s*(?:(?::|点|时)\\s*(\\d{1,2})\\s*分?|(?:点|时)?\\s*(半)|(?:点|时))?\\s*(?:到|至|\\-|~|—)\\s*(上午|早上|中午|下午|晚上|凌晨|傍晚)?\\s*(\\d{1,2})\\s*(?:(?::|点|时)\\s*(\\d{1,2})\\s*分?|(?:点|时)?\\s*(半)|(?:点|时))?");
    private static final Pattern HOURS_DURATION = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(小时|hour|hours|\\bh\\b)");
    private static final Pattern MINUTES_DURATION = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(分钟|min|mins|minute|minutes|\\bm\\b)");
    private static final Pattern HALF_HOUR = Pattern.compile("半\\s*小时");
    private static final Pattern MONEY_TOKEN = Pattern.compile(
            "(?i)(¥|￥|人民币|rmb)?\\s*(\\d+(?:\\.\\d+)?)\\s*(万|w|千|k|元|块|块钱|分)?");
    private static final Pattern EFF_SCORE = Pattern.compile("(?i)(?:效率|效能)\\s*[:：]?\\s*([0-9]{1,2}(?:\\.\\d+)?)\\s*分?");
    private static final Pattern VAL_SCORE = Pattern.compile("(?i)(?:价值|产出)\\s*[:：]?\\s*([0-9]{1,2}(?:\\.\\d+)?)\\s*分?");
    private static final Pattern STATE_SCORE = Pattern.compile("(?i)(?:状态|专注|精力)\\s*[:：]?\\s*([0-9]{1,2}(?:\\.\\d+)?)\\s*分?");
    private static final Pattern AI_RATIO = Pattern.compile("(?i)(?:AI|人工智能)(?:辅助|参与|占比|率)?\\s*[:：]?\\s*([0-9]{1,3}(?:\\.\\d+)?)\\s*%?");
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*[月/-]\\s*(\\d{1,2})\\s*日?");

    @Override
    public ParseResult parse(String rawText, String contextDate, ParserContext context) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ParseResult.empty("rule", "empty input");
        }
        String normalizedContextDate = normalizeContextDate(contextDate);
        List<String> lines = splitSegments(rawText);
        List<ParseDraftItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String line : lines) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String occurredOn = resolveDate(normalized, normalizedContextDate);
            ParseDraftItem parsed = tryParseLearning(normalized, occurredOn);
            if (parsed == null) {
                parsed = tryParseIncome(normalized, occurredOn);
            }
            if (parsed == null) {
                parsed = tryParseExpense(normalized, occurredOn);
            }
            if (parsed == null) {
                parsed = tryParseTimeLog(normalized, occurredOn);
            }
            if (parsed == null) {
                Map<String, String> unknownPayload = new HashMap<>();
                unknownPayload.put("date", occurredOn);
                unknownPayload.put("raw", normalized);
                parsed = new ParseDraftItem("unknown", unknownPayload, 0.2, "rule", "unrecognized");
                warnings.add("unrecognized line: " + normalized);
            }
            items.add(parsed);
        }
        return new ParseResult(null, items, warnings, "rule");
    }

    private ParseDraftItem tryParseTimeLog(String line, String occurredOn) {
        TimeRange range = parseTimeRange(line);
        Integer durationMinutes = parseDurationMinutes(line);
        if (range == null && durationMinutes == null) {
            return null;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(occurredOn));
        String description = extractTimeDescription(line);
        payload.put("description", description.isEmpty() ? safe(line) : description);
        payload.put("category", inferCategory(line));
        if (range != null) {
            payload.put("start_time", range.startTime);
            payload.put("end_time", range.endTime);
            if (durationMinutes == null || durationMinutes <= 0) {
                durationMinutes = range.durationMinutes;
            }
        }
        if (durationMinutes != null && durationMinutes > 0) {
            payload.put("duration_minutes", String.valueOf(durationMinutes));
        }
        attachOptionalScoresAndAiRatio(payload, line);
        String warning = range == null ? "no explicit time range" : null;
        double confidence = range == null ? 0.68 : 0.82;
        return new ParseDraftItem("time_log", payload, confidence, "rule", warning);
    }

    private ParseDraftItem tryParseIncome(String line, String occurredOn) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, "收入", "工资", "到账", "回款", "奖金", "报销", "转入")) {
            return null;
        }
        Money money = extractMoney(line);
        if (money == null) {
            return null;
        }
        String source = extractIncomeSource(line, money);
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(occurredOn));
        payload.put("source", source.isEmpty() ? "收入" : source);
        payload.put("amount", money.amountYuan);
        payload.put("type", inferIncomeType(line));
        if (line.contains("被动") || line.contains("自动收入")) {
            payload.put("is_passive", "true");
        }
        attachOptionalAiRatio(payload, line);
        return new ParseDraftItem("income", payload, money.explicitUnit ? 0.84 : 0.74, "rule", null);
    }

    private ParseDraftItem tryParseExpense(String line, String occurredOn) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, "花", "支出", "消费", "买", "付款", "付了", "开销")) {
            return null;
        }
        Money money = extractMoney(line);
        if (money == null) {
            return null;
        }
        String note = stripMoneyPhrase(line, money);
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(occurredOn));
        payload.put("amount", money.amountYuan);
        payload.put("category", inferExpenseCategory(note));
        payload.put("note", note.isEmpty() ? safe(line) : note);
        attachOptionalAiRatio(payload, line);
        return new ParseDraftItem("expense", payload, money.explicitUnit ? 0.84 : 0.74, "rule", null);
    }

    private ParseDraftItem tryParseLearning(String line, String occurredOn) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, "学习", "复习", "阅读", "课程", "刷题", "听课", "看书")) {
            return null;
        }
        TimeRange range = parseTimeRange(line);
        Integer durationMinutes = parseDurationMinutes(line);
        Map<String, String> payload = new HashMap<>();
        payload.put("date", safe(occurredOn));
        payload.put("content", extractLearningContent(line));
        payload.put("application_level", inferLearningLevel(line));
        attachOptionalScoresAndAiRatio(payload, line);
        if (range != null) {
            payload.put("start_time", range.startTime);
            payload.put("end_time", range.endTime);
            if (durationMinutes == null || durationMinutes <= 0) {
                durationMinutes = range.durationMinutes;
            }
        }
        if (durationMinutes != null && durationMinutes > 0) {
            payload.put("duration_minutes", String.valueOf(durationMinutes));
            return new ParseDraftItem("learning", payload, 0.84, "rule", null);
        }
        payload.put("duration_minutes", "60");
        return new ParseDraftItem("learning", payload, 0.62, "rule", "defaulted duration to 60m");
    }

    private static List<String> splitSegments(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String normalized = rawText.replace('\r', '\n');
        normalized = normalized.replace("；", "\n")
                .replace(";", "\n")
                .replace("。", "\n")
                .replace("！", "\n")
                .replace("？", "\n");
        normalized = normalized.replace("然后", "\n")
                .replace("接着", "\n")
                .replace("另外", "\n")
                .replace("再后来", "\n");

        String[] chunks = normalized.split("\\n+");
        List<String> out = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String line = chunk.trim();
            if (line.startsWith("-") || line.startsWith("•")) {
                line = line.substring(1).trim();
            }
            line = line.replaceAll("^(然后|接着|另外|还有|并且|再)\\s*", "");
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static String normalizeContextDate(String contextDate) {
        try {
            return LocalDate.parse(safe(contextDate)).toString();
        } catch (Exception ignored) {
            return LocalDate.now().toString();
        }
    }

    private static String resolveDate(String line, String contextDate) {
        LocalDate anchor = LocalDate.parse(normalizeContextDate(contextDate));
        String text = safe(line);
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

        Matcher full = FULL_DATE.matcher(text);
        if (full.find()) {
            try {
                int y = Integer.parseInt(full.group(1));
                int m = Integer.parseInt(full.group(2));
                int d = Integer.parseInt(full.group(3));
                return LocalDate.of(y, m, d).toString();
            } catch (Exception ignored) {
            }
        }

        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            try {
                int m = Integer.parseInt(monthDay.group(1));
                int d = Integer.parseInt(monthDay.group(2));
                return LocalDate.of(anchor.getYear(), m, d).toString();
            } catch (Exception ignored) {
            }
        }
        return anchor.toString();
    }

    private static TimeRange parseTimeRange(String line) {
        Matcher matcher = TIME_RANGE.matcher(safe(line));
        if (!matcher.find()) {
            return null;
        }
        String startPeriod = safe(matcher.group(1));
        String endPeriod = safe(matcher.group(5));
        if (startPeriod.isEmpty()) {
            startPeriod = endPeriod;
        }
        if (endPeriod.isEmpty()) {
            endPeriod = startPeriod;
        }
        String start = toClockTime(startPeriod, matcher.group(2), matcher.group(3), matcher.group(4));
        String end = toClockTime(endPeriod, matcher.group(6), matcher.group(7), matcher.group(8));
        if (start.isEmpty() || end.isEmpty()) {
            return null;
        }
        int durationMinutes;
        try {
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            durationMinutes = (int) Duration.between(s, e).toMinutes();
            if (durationMinutes <= 0) {
                durationMinutes += 24 * 60;
            }
        } catch (Exception ignored) {
            durationMinutes = 60;
        }
        if (durationMinutes <= 0) {
            durationMinutes = 60;
        }
        return new TimeRange(start, end, durationMinutes);
    }

    private static String toClockTime(String periodRaw, String hourRaw, String minuteRaw, String halfRaw) {
        int hour;
        try {
            hour = Integer.parseInt(safe(hourRaw));
        } catch (Exception ignored) {
            return "";
        }
        int minute = 0;
        if (!safe(minuteRaw).isEmpty()) {
            try {
                minute = Integer.parseInt(safe(minuteRaw));
            } catch (Exception ignored) {
                minute = 0;
            }
        } else if (!safe(halfRaw).isEmpty()) {
            minute = 30;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "";
        }

        String period = safe(periodRaw).toLowerCase(Locale.US);
        if (!period.isEmpty()) {
            if (period.contains("下午") || period.contains("晚上") || period.contains("傍晚")) {
                if (hour < 12) {
                    hour += 12;
                }
            } else if (period.contains("凌晨") || period.contains("早上") || period.contains("上午")) {
                if (hour == 12) {
                    hour = 0;
                }
            } else if (period.contains("中午")) {
                if (hour < 11) {
                    hour += 12;
                }
            }
        }
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private static Integer parseDurationMinutes(String line) {
        String text = safe(line);
        if (text.isEmpty()) {
            return null;
        }
        Matcher minutes = MINUTES_DURATION.matcher(text);
        if (minutes.find()) {
            Double value = parseDecimal(minutes.group(1));
            if (value != null && value > 0) {
                return Math.max(1, (int) Math.round(value));
            }
        }
        Matcher hours = HOURS_DURATION.matcher(text);
        if (hours.find()) {
            Double value = parseDecimal(hours.group(1));
            if (value != null && value > 0) {
                return Math.max(1, (int) Math.round(value * 60.0));
            }
        }
        if (HALF_HOUR.matcher(text).find()) {
            return 30;
        }
        return null;
    }

    private static Money extractMoney(String line) {
        String text = safe(line);
        Matcher matcher = MONEY_TOKEN.matcher(text);
        Money best = null;
        while (matcher.find()) {
            Double number = parseDecimal(matcher.group(2));
            if (number == null || number <= 0) {
                continue;
            }
            String symbol = safe(matcher.group(1));
            String unit = safe(matcher.group(3)).toLowerCase(Locale.US);
            boolean explicit = !symbol.isEmpty() || !unit.isEmpty();

            BigDecimal amount = BigDecimal.valueOf(number);
            if (unit.contains("万") || "w".equals(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(10_000L));
            } else if (unit.contains("千") || "k".equals(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(1_000L));
            } else if (unit.contains("分")) {
                amount = amount.divide(BigDecimal.valueOf(100L), 4, RoundingMode.HALF_UP);
            }
            String amountYuan = amount.stripTrailingZeros().toPlainString();
            Money current = new Money(amountYuan, explicit, matcher.group(), matcher.start(), matcher.end());

            if (best == null) {
                best = current;
                continue;
            }
            if (current.explicitUnit && !best.explicitUnit) {
                best = current;
                continue;
            }
            if (current.end >= best.end) {
                best = current;
            }
        }
        return best;
    }

    private static String extractIncomeSource(String line, Money money) {
        String text = stripMoneyPhrase(line, money);
        text = text.replaceAll("(今天|昨天|前天|明天|后天)", "").trim();
        text = text.replaceAll("(收入|到账|进账|回款|工资|奖金|报销)", "").trim();
        return text;
    }

    private static String stripMoneyPhrase(String line, Money money) {
        String text = safe(line);
        if (money == null || money.matchedText == null || money.matchedText.trim().isEmpty()) {
            return text;
        }
        return text.replace(money.matchedText, "").replaceAll("\\s{2,}", " ").trim();
    }

    private static String extractTimeDescription(String line) {
        String text = safe(line);
        Matcher matcher = TIME_RANGE.matcher(text);
        if (matcher.find()) {
            text = text.substring(0, matcher.start()) + text.substring(matcher.end());
        }
        text = text.replaceAll("(\\d+(?:\\.\\d+)?\\s*(小时|分钟|min|hour|h))", "");
        return text.trim();
    }

    private static String extractLearningContent(String line) {
        String text = extractTimeDescription(line);
        text = text.replaceAll("(学习|复习|阅读|课程|刷题|听课|看书)", "").trim();
        return text.isEmpty() ? safe(line) : text;
    }

    private static String inferCategory(String desc) {
        String lower = safe(desc).toLowerCase(Locale.ROOT);
        if (lower.contains("工作") || lower.contains("开发") || lower.contains("编码") || lower.contains("会议")
                || lower.contains("项目")) {
            return "work";
        }
        if (lower.contains("学习") || lower.contains("阅读") || lower.contains("课程")) {
            return "learning";
        }
        if (lower.contains("通勤") || lower.contains("做饭") || lower.contains("家务") || lower.contains("生活")) {
            return "life";
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
        return "work";
    }

    private static String inferIncomeType(String source) {
        String lower = safe(source).toLowerCase(Locale.ROOT);
        if (lower.contains("工资") || lower.contains("薪")) {
            return "salary";
        }
        if (lower.contains("项目") || lower.contains("外包") || lower.contains("回款")) {
            return "project";
        }
        if (lower.contains("投资") || lower.contains("分红")) {
            return "investment";
        }
        if (lower.contains("系统") || lower.contains("补贴")) {
            return "system";
        }
        return "other";
    }

    private static String inferExpenseCategory(String note) {
        String lower = safe(note).toLowerCase(Locale.ROOT);
        if (lower.contains("会员") || lower.contains("订阅")) {
            return "subscription";
        }
        if (lower.contains("投资") || lower.contains("理财")) {
            return "investment";
        }
        if (lower.contains("咖啡") || lower.contains("吃") || lower.contains("玩") || lower.contains("旅行")
                || lower.contains("电影") || lower.contains("聚餐")) {
            return "experience";
        }
        return "necessary";
    }

    private static String inferLearningLevel(String line) {
        String lower = safe(line).toLowerCase(Locale.ROOT);
        if (lower.contains("成果") || lower.contains("产出")) {
            return "result";
        }
        if (lower.contains("实践") || lower.contains("应用") || lower.contains("落地")) {
            return "applied";
        }
        return "input";
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
            Integer value = parseScoreOrNull(eff.group(1));
            if (value != null) {
                payload.put("efficiency_score", String.valueOf(value));
            }
        }
        Matcher val = VAL_SCORE.matcher(text);
        if (val.find()) {
            Integer value = parseScoreOrNull(val.group(1));
            if (value != null) {
                payload.put("value_score", String.valueOf(value));
            }
        }
        Matcher state = STATE_SCORE.matcher(text);
        if (state.find()) {
            Integer value = parseScoreOrNull(state.group(1));
            if (value != null) {
                payload.put("state_score", String.valueOf(value));
            }
        }
        attachOptionalAiRatio(payload, text);
    }

    private static void attachOptionalAiRatio(Map<String, String> payload, String line) {
        if (payload == null) {
            return;
        }
        Matcher ai = AI_RATIO.matcher(safe(line));
        if (ai.find()) {
            Integer value = parsePercentageOrNull(ai.group(1));
            if (value != null) {
                payload.put("ai_ratio", String.valueOf(value));
            }
        }
    }

    private static Integer parsePercentageOrNull(String raw) {
        Double value = parseDecimal(raw);
        if (value == null) {
            return null;
        }
        if (value > 0 && value <= 1.0 && safe(raw).contains(".")) {
            value = value * 100.0;
        }
        int n = (int) Math.round(value);
        if (n < 0 || n > 100) {
            return null;
        }
        return n;
    }

    private static Integer parseScoreOrNull(String raw) {
        Double value = parseDecimal(raw);
        if (value == null) {
            return null;
        }
        int n = (int) Math.round(value);
        if (n < 1 || n > 10) {
            return null;
        }
        return n;
    }

    private static Double parseDecimal(String raw) {
        try {
            return Double.parseDouble(safe(raw).replace(",", "").replace("，", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.trim().isEmpty() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static final class Money {
        final String amountYuan;
        final boolean explicitUnit;
        final String matchedText;
        final int start;
        final int end;

        Money(String amountYuan, boolean explicitUnit, String matchedText, int start, int end) {
            this.amountYuan = amountYuan;
            this.explicitUnit = explicitUnit;
            this.matchedText = matchedText;
            this.start = start;
            this.end = end;
        }
    }

    private static final class TimeRange {
        final String startTime;
        final String endTime;
        final int durationMinutes;

        TimeRange(String startTime, String endTime, int durationMinutes) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
        }
    }
}
