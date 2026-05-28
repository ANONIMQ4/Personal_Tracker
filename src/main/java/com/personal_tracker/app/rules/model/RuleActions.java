package com.personal_tracker.app.rules.model;

public record RuleActions(
        String setCategory,
        boolean excludeFromAnalytics,
        String setCounterparty,
        boolean markAsTransfer,
        String renameDescription
) {
    public RuleActions {
        setCategory = blankToNull(setCategory);
        setCounterparty = blankToNull(setCounterparty);
        renameDescription = blankToNull(renameDescription);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
