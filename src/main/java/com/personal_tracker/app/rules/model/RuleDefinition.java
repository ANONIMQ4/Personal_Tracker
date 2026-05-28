package com.personal_tracker.app.rules.model;

public record RuleDefinition(
        String name,
        double confidence,
        RuleConditions conditions,
        RuleActions actions
) {
}
