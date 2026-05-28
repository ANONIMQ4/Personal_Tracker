package com.personal_tracker.app.rules.dto;

import com.personal_tracker.app.rules.model.RuleDefinition;

import java.time.LocalDateTime;

public record RuleDto(
        Long id,
        String name,
        String originalPrompt,
        RuleDefinition rule,
        boolean enabled,
        long affectedCount,
        long lastAppliedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
