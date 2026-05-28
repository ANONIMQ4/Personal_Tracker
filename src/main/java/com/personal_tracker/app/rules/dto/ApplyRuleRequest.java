package com.personal_tracker.app.rules.dto;

import com.personal_tracker.app.rules.model.RuleDefinition;

public record ApplyRuleRequest(
        RuleDefinition rule,
        boolean saveRule,
        String originalPrompt
) {
}
