package com.example.skyeos.ai;

import java.util.Collections;
import java.util.Map;

public final class ParseDraftItem {
    public final String kind;
    public final Map<String, String> payload;
    public final double confidence;
    public final String source;
    public final String warning;

    public ParseDraftItem(String kind, Map<String, String> payload, double confidence, String source, String warning) {
        this.kind = kind;
        this.payload = payload == null ? Collections.emptyMap() : payload;
        this.confidence = confidence;
        this.source = source;
        this.warning = warning;
    }
}

