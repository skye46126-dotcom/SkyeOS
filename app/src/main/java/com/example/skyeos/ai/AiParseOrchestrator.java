package com.example.skyeos.ai;

public final class AiParseOrchestrator {
    private final ParserEngine llmEngine;
    private final ParserEngine vcpEngine;
    private final ParserEngine ruleEngine;
    private ParserMode parserMode;

    public AiParseOrchestrator(ParserEngine llmEngine, ParserEngine vcpEngine, ParserEngine ruleEngine,
            ParserMode parserMode) {
        this.llmEngine = llmEngine;
        this.vcpEngine = vcpEngine;
        this.ruleEngine = ruleEngine;
        this.parserMode = parserMode == null ? ParserMode.AUTO : parserMode;
    }

    public void setParserMode(ParserMode mode) {
        this.parserMode = mode == null ? ParserMode.AUTO : mode;
    }

    public ParserMode getParserMode() {
        return parserMode;
    }

    public ParseResult parse(String rawText, String contextDate, ParserContext context) {
        try {
            switch (parserMode) {
                case RULE:
                    return ruleEngine.parse(rawText, contextDate, context);
                case VCP:
                    return vcpEngine.parse(rawText, contextDate, context);
                case AUTO:
                default:
                    return parseAuto(rawText, contextDate, context);
            }
        } catch (Exception e) {
            return ParseResult.empty("orchestrator", e.getMessage());
        }
    }

    private ParseResult parseAuto(String rawText, String contextDate, ParserContext context) {
        ParseResult rule = null;
        try {
            rule = ruleEngine.parse(rawText, contextDate, context);
        } catch (Exception ignored) {
        }

        // Prepare context for LLM with rule hints
        ParserContext llmContext = context != null ? context : new ParserContext();
        if (rule != null && rule.items != null) {
            for (ParseDraftItem item : rule.items) {
                if (!"unknown".equals(item.kind)) {
                    llmContext.addRuleHint(item);
                }
            }
        }

        try {
            ParseResult llm = llmEngine.parse(rawText, contextDate, llmContext);
            if (rule == null || rule.items == null || rule.items.isEmpty()) {
                return llm;
            }
            return mergeResults(llm, rule);
        } catch (Exception llmError) {
            try {
                ParseResult fallback = rule != null ? rule : ruleEngine.parse(rawText, contextDate, context);
                return withWarning(fallback, "fallback to rule parser: " + llmError.getMessage());
            } catch (Exception ruleError) {
                return ParseResult.empty("auto",
                        "llm failed: " + llmError.getMessage() + "; rule failed: " + ruleError.getMessage());
            }
        }
    }

    private static ParseResult withWarning(ParseResult result, String warning) {
        java.util.List<String> ws = new java.util.ArrayList<>(result.warnings);
        if (warning != null && !warning.trim().isEmpty()) {
            ws.add(warning.trim());
        }
        return new ParseResult(result.requestId, result.items, ws, result.parserUsed);
    }

    private static ParseResult mergeResults(ParseResult primary, ParseResult secondary) {
        java.util.List<ParseDraftItem> merged = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        appendItems(primary.items, merged, seen);
        appendItems(secondary.items, merged, seen);

        java.util.List<String> ws = new java.util.ArrayList<>();
        if (primary.warnings != null) {
            ws.addAll(primary.warnings);
        }
        if (secondary.warnings != null) {
            ws.addAll(secondary.warnings);
        }
        ws.add("auto merge: llm + rule");
        return new ParseResult(primary.requestId, merged, ws, "auto_merge");
    }

    private static void appendItems(
            java.util.List<ParseDraftItem> from,
            java.util.List<ParseDraftItem> into,
            java.util.Set<String> seen) {
        if (from == null) {
            return;
        }
        for (ParseDraftItem item : from) {
            if (item == null) {
                continue;
            }
            String signature = buildSignature(item);
            if (seen.contains(signature)) {
                continue;
            }
            seen.add(signature);
            into.add(item);
        }
    }

    private static String buildSignature(ParseDraftItem item) {
        java.util.List<String> keys = new java.util.ArrayList<>(item.payload.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        sb.append(item.kind == null ? "unknown" : item.kind).append('|');
        for (String key : keys) {
            sb.append(key).append('=').append(item.payload.get(key)).append(';');
        }
        return sb.toString();
    }
}
