package com.example.skyeos.ai;

public final class AiParseOrchestrator {
    private final ParserEngine vcpEngine;
    private final ParserEngine ruleEngine;
    private ParserMode parserMode;

    public AiParseOrchestrator(ParserEngine vcpEngine, ParserEngine ruleEngine, ParserMode parserMode) {
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

    public ParseResult parse(String rawText, String contextDate) {
        try {
            switch (parserMode) {
                case RULE:
                    return ruleEngine.parse(rawText, contextDate);
                case VCP:
                    return vcpEngine.parse(rawText, contextDate);
                case AUTO:
                default:
                    return parseAuto(rawText, contextDate);
            }
        } catch (Exception e) {
            return ParseResult.empty("orchestrator", e.getMessage());
        }
    }

    private ParseResult parseAuto(String rawText, String contextDate) {
        try {
            return vcpEngine.parse(rawText, contextDate);
        } catch (Exception vcpError) {
            try {
                ParseResult fallback = ruleEngine.parse(rawText, contextDate);
                return new ParseResult(
                        fallback.requestId,
                        fallback.items,
                        appendWarning(fallback, "fallback to rule parser: " + vcpError.getMessage()),
                        fallback.parserUsed
                );
            } catch (Exception ruleError) {
                return ParseResult.empty("auto", "vcp failed: " + vcpError.getMessage() + "; rule failed: " + ruleError.getMessage());
            }
        }
    }

    private static java.util.List<String> appendWarning(ParseResult result, String warning) {
        java.util.List<String> ws = new java.util.ArrayList<>(result.warnings);
        if (warning != null && !warning.trim().isEmpty()) {
            ws.add(warning.trim());
        }
        return ws;
    }
}

