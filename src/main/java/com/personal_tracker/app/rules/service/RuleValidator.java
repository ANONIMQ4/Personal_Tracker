package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RuleValidator {

    public static final double MIN_CONFIDENCE = 0;

    private static final Set<String> BASE_ALLOWED_CATEGORIES = Set.of(
            "Переводы",
            "Входящие переводы",
            "Исходящие переводы",
            "Супермаркеты",
            "Маркетплейсы",
            "Медицина",
            "Аптеки",
            "Транспорт",
            "Местный транспорт",
            "Такси",
            "Ж/д билеты",
            "ЖД билеты",
            "Авиабилеты",
            "Образование",
            "Фастфуд",
            "Связь",
            "Услуги банка",
            "Сервис",
            "Цифровые товары",
            "Игры",
            "Steam",
            "Кэшбэк",
            "Возвраты",
            "Пополнения",
            "Зарплата",
            "Бонусы",
            "Прочий доход",
            "Прочий расход",
            "Остальное",
            "Без категории"
    );

    public void validate(RuleDefinition rule, Collection<String> userCategories) {
        if (rule == null) {
            throw new IllegalArgumentException("Правило не распознано");
        }
        if (rule.name() == null || rule.name().isBlank()) {
            throw new IllegalArgumentException("У правила должно быть имя");
        }
        if (rule.confidence() < MIN_CONFIDENCE) {
            throw new IllegalArgumentException("Низкая уверенность распознавания");
        }
        validateConditions(rule.conditions());
        validateActions(rule.actions());
        validateCategories(rule, userCategories);
    }

    public Set<String> allowedCategories(Collection<String> userCategories) {
        Set<String> categories = new LinkedHashSet<>(BASE_ALLOWED_CATEGORIES);
        if (userCategories != null) {
            userCategories.stream()
                    .filter(category -> category != null && !category.isBlank())
                    .map(String::trim)
                    .forEach(categories::add);
        }
        return categories;
    }

    private void validateConditions(RuleConditions conditions) {
        if (conditions == null) {
            throw new IllegalArgumentException("У правила нет условий");
        }
        String type = conditions.type();
        if (!List.of("income", "expense", "all").contains(type)) {
            throw new IllegalArgumentException("Некорректный тип операции");
        }
        BigDecimal amountMin = conditions.amountMin();
        BigDecimal amountMax = conditions.amountMax();
        if (amountMin != null && amountMax != null && amountMax.compareTo(amountMin) < 0) {
            throw new IllegalArgumentException("Некорректный диапазон суммы");
        }
        boolean hasCondition = !conditions.descriptionContains().isEmpty()
                || !conditions.categoryIn().isEmpty()
                || !"all".equals(type)
                || amountMin != null
                || amountMax != null
                || !conditions.counterpartyContains().isEmpty();
        if (!hasCondition) {
            throw new IllegalArgumentException("У правила должно быть хотя бы одно условие");
        }
    }

    private void validateActions(RuleActions actions) {
        if (actions == null) {
            throw new IllegalArgumentException("У правила нет действий");
        }
        boolean hasAction = actions.setCategory() != null
                || actions.excludeFromAnalytics()
                || actions.setCounterparty() != null
                || actions.markAsTransfer()
                || actions.renameDescription() != null;
        if (!hasAction) {
            throw new IllegalArgumentException("У правила должно быть хотя бы одно действие");
        }
    }

    private void validateCategories(RuleDefinition rule, Collection<String> userCategories) {
        Set<String> allowedCategories = allowedCategories(userCategories);
        for (String category : rule.conditions().categoryIn()) {
            if (!allowedCategories.contains(category)) {
                throw new IllegalArgumentException("Неизвестная категория в условиях: " + category);
            }
        }
        String setCategory = rule.actions().setCategory();
        if (setCategory != null && !allowedCategories.contains(setCategory)) {
            throw new IllegalArgumentException("Неизвестная категория в действии: " + setCategory);
        }
    }
}
