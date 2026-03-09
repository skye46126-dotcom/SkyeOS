package com.example.skyeos.ai;

public interface ParserEngine {
    ParseResult parse(String rawText, String contextDate, ParserContext context) throws Exception;
}
