package com.personal_tracker.app.rules.llm;

import com.personal_tracker.app.rules.model.RuleDefinition;

import java.util.Collection;

public interface RuleParserClient {
    RuleDefinition parse(String userPrompt, Collection<String> categories);

    default boolean usedFallback() {
        return false;
    }
}
