package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.rules.dto.RulePreviewResponse.OperationState;
import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class RuleEngine {

    public boolean matches(FinanceOperation operation, RuleDefinition rule) {
        RuleConditions conditions = rule.conditions();
        return matchesType(operation, conditions.type())
                && containsAny(operation.getDescription(), conditions.descriptionContains())
                && categoryMatches(operation, conditions)
                && amountMatches(operation, conditions)
                && containsAny(operation.getCounterparty(), conditions.counterpartyContains());
    }

    public OperationState stateOf(FinanceOperation operation) {
        return new OperationState(
                operation.getCategory(),
                operation.isExcludeFromAnalytics(),
                operation.getCounterparty(),
                operation.getDescription()
        );
    }

    public OperationState previewAfter(FinanceOperation operation, RuleDefinition rule) {
        RuleActions actions = rule.actions();
        String category = operation.getCategory();
        String counterparty = operation.getCounterparty();
        String description = operation.getDescription();
        boolean excludeFromAnalytics = operation.isExcludeFromAnalytics();

        if (actions.setCategory() != null) {
            category = normalizeCategory(actions.setCategory());
        }
        if (actions.markAsTransfer()) {
            category = "Переводы";
        }
        if (actions.setCounterparty() != null) {
            counterparty = actions.setCounterparty();
        }
        if (actions.renameDescription() != null) {
            description = actions.renameDescription();
        }
        if (actions.excludeFromAnalytics()) {
            excludeFromAnalytics = true;
        }

        return new OperationState(category, excludeFromAnalytics, counterparty, description);
    }

    public void apply(FinanceOperation operation, RuleDefinition rule) {
        OperationState after = previewAfter(operation, rule);
        restore(operation, after);
    }

    public void restore(FinanceOperation operation, OperationState state) {
        operation.setCategory(state.category());
        operation.setExcludeFromAnalytics(state.excludeFromAnalytics());
        operation.setCounterparty(state.counterparty());
        operation.setDescription(state.description());
    }

    private boolean matchesType(FinanceOperation operation, String type) {
        BigDecimal amount = operation.getOperationAmount();
        if (amount == null || "all".equals(type)) {
            return true;
        }
        if ("income".equals(type)) {
            return amount.signum() >= 0;
        }
        if ("expense".equals(type)) {
            return amount.signum() < 0;
        }
        return false;
    }

    private boolean categoryMatches(FinanceOperation operation, RuleConditions conditions) {
        if (conditions.categoryIn().isEmpty()) {
            return true;
        }
        String category = operation.getCategory() == null ? "Без категории" : operation.getCategory();
        return conditions.categoryIn().stream().anyMatch(conditionCategory -> RuleCategoryMatcher.same(category, conditionCategory));
    }

    private boolean amountMatches(FinanceOperation operation, RuleConditions conditions) {
        BigDecimal amount = operation.getOperationAmount() == null
                ? BigDecimal.ZERO
                : operation.getOperationAmount().abs();
        return (conditions.amountMin() == null || amount.compareTo(conditions.amountMin()) >= 0)
                && (conditions.amountMax() == null || amount.compareTo(conditions.amountMax()) <= 0);
    }

    private boolean containsAny(String value, java.util.List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return true;
        }
        String normalizedValue = normalize(value);
        return needles.stream()
                .filter(needle -> needle != null && !needle.isBlank())
                .map(this::normalize)
                .anyMatch(normalizedValue::contains);
    }

    private String normalize(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .trim();
    }

    private String normalizeCategory(String category) {
        if (RuleCategoryMatcher.same(category, "Входящие переводы") || RuleCategoryMatcher.same(category, "Исходящие переводы")) {
            return "Переводы";
        }
        return category;
    }
}
