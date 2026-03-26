package com.example.skyeos.di;

import com.example.skyeos.ai.AiApiConfigStore;
import com.example.skyeos.ai.AiParseOrchestrator;
import com.example.skyeos.ai.LlmApiParserEngine;
import com.example.skyeos.ai.ParserEngine;
import com.example.skyeos.ai.ParserMode;
import com.example.skyeos.ai.ParserSettingsStore;
import com.example.skyeos.ai.RuleParserEngine;
import com.example.skyeos.ai.VcpParserEngine;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LlmEngine {}

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RuleEngine {}

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    public @interface VcpEngine {}

    @Provides
    @LlmEngine
    static ParserEngine provideLlmEngine(LlmApiParserEngine engine) {
        return engine;
    }

    @Provides
    @RuleEngine
    static ParserEngine provideRuleEngine(RuleParserEngine engine) {
        return engine;
    }

    @Provides
    @VcpEngine
    static ParserEngine provideVcpEngine(VcpParserEngine engine) {
        return engine;
    }

    @Provides
    static AiParseOrchestrator provideOrchestrator(
            @LlmEngine ParserEngine llm,
            @VcpEngine ParserEngine vcp,
            @RuleEngine ParserEngine rule,
            ParserSettingsStore settings) {
        return new AiParseOrchestrator(llm, vcp, rule, settings.loadMode());
    }
}
