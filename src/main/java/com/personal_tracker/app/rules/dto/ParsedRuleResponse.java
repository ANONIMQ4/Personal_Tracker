package com.personal_tracker.app.rules.dto;

import com.personal_tracker.app.rules.model.RuleDefinition;

import java.util.List;

public record ParsedRuleResponse(
        RuleDefinition rule,
        List<String> warnings
) {
}
