package com.personal_tracker.app.rules.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RulePreviewResponse(
        long affectedCount,
        List<RulePreviewChange> changes
) {
    public record RulePreviewChange(
            Long operationId,
            LocalDateTime date,
            String description,
            OperationState before,
            OperationState after
    ) {
    }

    public record OperationState(
            String category,
            boolean excludeFromAnalytics,
            String counterparty,
            String description
    ) {
    }
}
