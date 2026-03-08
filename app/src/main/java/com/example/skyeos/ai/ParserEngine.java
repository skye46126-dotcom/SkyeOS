package com.example.skyeos.ai;

public interface ParserEngine {
    ParseResult parse(String rawText, String contextDate) throws Exception;
}

