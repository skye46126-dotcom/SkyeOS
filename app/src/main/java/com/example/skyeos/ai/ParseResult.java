package com.example.skyeos.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ParseResult {
    public final String requestId;
    public final List<ParseDraftItem> items;
    public final List<String> warnings;
    public final String parserUsed;

    public ParseResult(String requestId, List<ParseDraftItem> items, List<String> warnings, String parserUsed) {
        this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        this.items = items == null ? Collections.emptyList() : items;
        this.warnings = warnings == null ? Collections.emptyList() : warnings;
        this.parserUsed = parserUsed == null ? "unknown" : parserUsed;
    }

    public static ParseResult empty(String parserUsed, String warning) {
        List<String> ws = new ArrayList<>();
        if (warning != null && !warning.trim().isEmpty()) {
            ws.add(warning.trim());
        }
        return new ParseResult(UUID.randomUUID().toString(), Collections.emptyList(), ws, parserUsed);
    }
}

