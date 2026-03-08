package com.example.skyeos.ai;

public enum ParserMode {
    AUTO,
    VCP,
    RULE;

    public static ParserMode fromString(String raw) {
        if (raw == null) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "vcp":
                return VCP;
            case "rule":
                return RULE;
            case "auto":
            default:
                return AUTO;
        }
    }
}

