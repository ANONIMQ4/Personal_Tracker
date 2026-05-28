package com.personal_tracker.app.rules.model;

import java.math.BigDecimal;
import java.util.List;

public record RuleConditions(
        List<String> descriptionContains,
        List<String> categoryIn,
        String type,
        BigDecimal amountMin,
        BigDecimal amountMax,
        List<String> counterpartyContains
) {
    public RuleConditions {
        descriptionContains = descriptionContains == null ? List.of() : List.copyOf(descriptionContains);
        categoryIn = categoryIn == null ? List.of() : List.copyOf(categoryIn);
        type = type == null || type.isBlank() ? "all" : type.trim();
        counterpartyContains = counterpartyContains == null ? List.of() : List.copyOf(counterpartyContains);
    }
}
