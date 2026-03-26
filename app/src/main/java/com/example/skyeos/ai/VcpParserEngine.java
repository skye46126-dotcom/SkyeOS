package com.example.skyeos.ai;

public final class VcpParserEngine implements ParserEngine {
    @javax.inject.Inject
    public VcpParserEngine() {}
    @Override
    public ParseResult parse(String rawText, String contextDate, ParserContext context) {
        throw new UnsupportedOperationException("VCP parser is not wired yet");
    }
}
