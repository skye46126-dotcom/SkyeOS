package com.example.skyeos.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RuleParserEngineTest {

    @Test
    public void parseSpokenMixedInput_shouldExtractFourKinds() {
        RuleParserEngine engine = new RuleParserEngine();
        String raw = "今天上午9点半到11点20写需求文档。"
                + "下午学习1.5小时AI课程。"
                + "晚饭花了38块。"
                + "项目回款5000元。";

        ParseResult result = engine.parse(raw, "2026-03-26", new ParserContext());

        ParseDraftItem time = findFirstByKind(result, "time_log");
        ParseDraftItem learning = findFirstByKind(result, "learning");
        ParseDraftItem expense = findFirstByKind(result, "expense");
        ParseDraftItem income = findFirstByKind(result, "income");

        assertNotNull(time);
        assertNotNull(learning);
        assertNotNull(expense);
        assertNotNull(income);

        assertEquals("09:30", time.payload.get("start_time"));
        assertEquals("11:20", time.payload.get("end_time"));
        assertEquals("90", learning.payload.get("duration_minutes"));
        assertEquals("38", expense.payload.get("amount"));
        assertEquals("5000", income.payload.get("amount"));
    }

    @Test
    public void parseRelativeDateAndEveningRange_shouldUseContextDateMinusOneDay() {
        RuleParserEngine engine = new RuleParserEngine();
        String raw = "昨天晚上8点到10点学习算法";

        ParseResult result = engine.parse(raw, "2026-03-26", new ParserContext());
        ParseDraftItem learning = findFirstByKind(result, "learning");

        assertNotNull(learning);
        assertEquals("2026-03-25", learning.payload.get("date"));
        assertEquals("20:00", learning.payload.get("start_time"));
        assertEquals("22:00", learning.payload.get("end_time"));
    }

    @Test
    public void parseAmountWithWanUnit_shouldConvertToYuanNumberString() {
        RuleParserEngine engine = new RuleParserEngine();
        String raw = "今天项目回款1.2万";

        ParseResult result = engine.parse(raw, "2026-03-26", new ParserContext());
        ParseDraftItem income = findFirstByKind(result, "income");

        assertNotNull(income);
        assertEquals("12000", income.payload.get("amount"));
        assertEquals("project", income.payload.get("type"));
    }

    private static ParseDraftItem findFirstByKind(ParseResult result, String kind) {
        if (result == null || result.items == null) {
            return null;
        }
        for (ParseDraftItem item : result.items) {
            if (item == null || item.kind == null) {
                continue;
            }
            if (kind.equals(item.kind)) {
                return item;
            }
        }
        return null;
    }

}
