package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private final RuleEngine ruleEngine = new RuleEngine();
    private final RuleValidator validator = new RuleValidator();

    @Test
    void categoryToCategoryRuleMatchesByCurrentCategoryAndAppliesTargetCategory() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of(), List.of("Экосистема Яндекс"), "all", null, null, List.of()),
                new RuleActions("Цифровые товары", false, null, false, null)
        );

        FinanceOperation matchingOperation = operation("Экосистема Яндекс", "Yandex Plus", "-399.00");
        FinanceOperation descriptionOnlyOperation = operation("Подписки", "Экосистема Яндекс", "-399.00");

        assertThat(ruleEngine.matches(matchingOperation, rule)).isTrue();
        assertThat(ruleEngine.matches(descriptionOnlyOperation, rule)).isFalse();

        ruleEngine.apply(matchingOperation, rule);
        assertThat(matchingOperation.getCategory()).isEqualTo("Цифровые товары");
        assertThat(matchingOperation.getDescription()).isEqualTo("Yandex Plus");
    }

    @Test
    void descriptionToCategoryRuleMatchesByDescriptionAndAppliesCategory() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of("Яндекс Такси"), List.of(), "all", null, null, List.of()),
                new RuleActions("Такси", false, null, false, null)
        );

        FinanceOperation matchingOperation = operation("Транспорт", "Яндекс Такси", "-450.00");
        FinanceOperation unrelatedOperation = operation("Транспорт", "Такси Парк", "-450.00");

        assertThat(ruleEngine.matches(matchingOperation, rule)).isTrue();
        assertThat(ruleEngine.matches(unrelatedOperation, rule)).isFalse();

        ruleEngine.apply(matchingOperation, rule);
        assertThat(matchingOperation.getCategory()).isEqualTo("Такси");
    }

    @Test
    void multiCategoryRuleMatchesByCurrentCategoriesAndAppliesTargetCategory() {
        RuleDefinition rule = rule(
                new RuleConditions(
                        List.of(),
                        List.of("ЖД билеты", "Авиабилеты", "Такси", "Местный транспорт"),
                        "expense",
                        null,
                        null,
                        List.of()
                ),
                new RuleActions("Транспорт", false, null, false, null)
        );

        FinanceOperation rail = operation("ЖД билеты", "Демо расход", "-65.00");
        FinanceOperation flight = operation("Авиабилеты", "Демо расход", "-7000.00");
        FinanceOperation taxi = operation("Такси", "Демо расход", "-450.00");
        FinanceOperation localTransport = operation("Местный транспорт", "Демо расход", "-65.00");
        FinanceOperation supermarket = operation("Супермаркеты", "Демо расход", "-1200.00");
        FinanceOperation taxiIncome = operation("Такси", "Возврат", "450.00");

        assertThat(ruleEngine.matches(rail, rule)).isTrue();
        assertThat(ruleEngine.matches(flight, rule)).isTrue();
        assertThat(ruleEngine.matches(taxi, rule)).isTrue();
        assertThat(ruleEngine.matches(localTransport, rule)).isTrue();
        assertThat(ruleEngine.matches(supermarket, rule)).isFalse();
        assertThat(ruleEngine.matches(taxiIncome, rule)).isFalse();

        List.of(rail, flight, taxi, localTransport).forEach(operation -> ruleEngine.apply(operation, rule));

        assertThat(rail.getCategory()).isEqualTo("Транспорт");
        assertThat(flight.getCategory()).isEqualTo("Транспорт");
        assertThat(taxi.getCategory()).isEqualTo("Транспорт");
        assertThat(localTransport.getCategory()).isEqualTo("Транспорт");
    }

    @Test
    void railTicketRuleToleratesCategoryAliases() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of(), List.of("жд билеты"), "expense", null, null, List.of()),
                new RuleActions("Транспорт", false, null, false, null)
        );

        FinanceOperation railWithSlash = operation("Ж/д билеты", "Яндекс Расписания", "-65.00");
        FinanceOperation railWithoutSlash = operation("ЖД билеты", "РЖД", "-1200.00");
        FinanceOperation railIncome = operation("Ж/д билеты", "Возврат билета", "65.00");

        assertThat(ruleEngine.matches(railWithSlash, rule)).isTrue();
        assertThat(ruleEngine.matches(railWithoutSlash, rule)).isTrue();
        assertThat(ruleEngine.matches(railIncome, rule)).isFalse();

        ruleEngine.apply(railWithSlash, rule);
        ruleEngine.apply(railWithoutSlash, rule);

        assertThat(railWithSlash.getCategory()).isEqualTo("Транспорт");
        assertThat(railWithoutSlash.getCategory()).isEqualTo("Транспорт");
    }

    @Test
    void categoryMatcherCanonicalizesAgainstAllowedUserCategories() {
        List<String> allowedCategories = List.of("Ж/д билеты", "Транспорт", "Переводы", "Новая категория");

        assertThat(RuleCategoryMatcher.canonicalize("жд билеты", allowedCategories)).isEqualTo("Ж/д билеты");
        assertThat(RuleCategoryMatcher.canonicalize("Ж/Д билеты", allowedCategories)).isEqualTo("Ж/д билеты");
        assertThat(RuleCategoryMatcher.canonicalize("траспорт", allowedCategories)).isEqualTo("Транспорт");
        assertThat(RuleCategoryMatcher.canonicalize("Новая категория", allowedCategories)).isEqualTo("Новая категория");
        assertThat(RuleCategoryMatcher.canonicalize("Категория которой нет", allowedCategories)).isEqualTo("Категория которой нет");
        assertThat(RuleCategoryMatcher.canonicalize("Длинная категория", List.of("Длинная категория А", "Длинная категория Б")))
                .isEqualTo("Длинная категория");
    }

    @Test
    void outgoingTransferRuleMatchesOnlyExpenses() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of("Т-Банк"), List.of(), "expense", null, null, List.of()),
                new RuleActions("Переводы", false, null, false, null)
        );

        FinanceOperation outgoing = operation("Различные услуги", "Т-Банк", "-5000.00");
        FinanceOperation incoming = operation("Различные услуги", "Т-Банк", "5000.00");

        assertThat(ruleEngine.matches(outgoing, rule)).isTrue();
        assertThat(ruleEngine.matches(incoming, rule)).isFalse();

        ruleEngine.apply(outgoing, rule);
        assertThat(outgoing.getCategory()).isEqualTo("Переводы");
    }

    @Test
    void renameDescriptionRuleChangesDescriptionWithoutChangingCategory() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of("Yandex Plus"), List.of(), "all", null, null, List.of()),
                new RuleActions(null, false, null, false, "Подписки")
        );

        FinanceOperation operation = operation("Экосистема Яндекс", "Yandex Plus", "-399.00");

        assertThat(ruleEngine.matches(operation, rule)).isTrue();
        ruleEngine.apply(operation, rule);

        assertThat(operation.getDescription()).isEqualTo("Подписки");
        assertThat(operation.getCategory()).isEqualTo("Экосистема Яндекс");
    }

    @Test
    void cashbackRuleMatchesOnlyIncomeAndAppliesCashbackCategory() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of("кэшбэк", "cashback"), List.of("Бонусы", "Кэшбэк"), "income", null, null, List.of()),
                new RuleActions("Кэшбэк", false, null, false, null)
        );

        FinanceOperation incomeCashback = operation("Бонусы", "Зачисление кэшбэка", "184.00");
        FinanceOperation expenseCashback = operation("Бонусы", "Списание кэшбэка", "-184.00");

        assertThat(ruleEngine.matches(incomeCashback, rule)).isTrue();
        assertThat(ruleEngine.matches(expenseCashback, rule)).isFalse();

        ruleEngine.apply(incomeCashback, rule);
        assertThat(incomeCashback.getCategory()).isEqualTo("Кэшбэк");
    }

    @Test
    void brokerExclusionRuleExcludesMatchingOperationFromAnalytics() {
        RuleDefinition rule = rule(
                new RuleConditions(List.of("брокер", "брокерский счет"), List.of(), "all", null, null, List.of()),
                new RuleActions(null, true, null, false, null)
        );

        FinanceOperation brokerOperation = operation("Переводы", "Вывод с брокерского счета", "4721.00");
        FinanceOperation normalTransfer = operation("Переводы", "Входящий перевод", "4721.00");

        assertThat(ruleEngine.matches(brokerOperation, rule)).isTrue();
        assertThat(ruleEngine.matches(normalTransfer, rule)).isFalse();

        ruleEngine.apply(brokerOperation, rule);
        assertThat(brokerOperation.isExcludeFromAnalytics()).isTrue();
        assertThat(brokerOperation.getCategory()).isEqualTo("Переводы");
    }

    @Test
    void validatorDoesNotBlockLowConfidenceWhenRuleIsStructurallyValid() {
        RuleDefinition lowConfidenceRule = new RuleDefinition(
                "Low confidence but valid",
                0.4,
                new RuleConditions(List.of("что-то странное"), List.of(), "all", null, null, List.of()),
                new RuleActions("Игры", false, null, false, null)
        );

        assertThatNoException().isThrownBy(() -> validator.validate(lowConfidenceRule, List.of("Игры")));
    }

    @Test
    void validatorAllowsNewTargetCategoryButRejectsUnknownSourceCategory() {
        RuleDefinition newTargetCategoryRule = new RuleDefinition(
                "New target category",
                0.9,
                new RuleConditions(List.of("steam"), List.of(), "all", null, null, List.of()),
                new RuleActions("Игры", false, null, false, null)
        );
        RuleDefinition unknownSourceCategoryRule = new RuleDefinition(
                "Unknown source category",
                0.9,
                new RuleConditions(List.of(), List.of("Игры"), "all", null, null, List.of()),
                new RuleActions("Супермаркеты", false, null, false, null)
        );

        assertThatNoException().isThrownBy(() -> validator.validate(newTargetCategoryRule, List.of("Супермаркеты")));
        assertThatThrownBy(() -> validator.validate(unknownSourceCategoryRule, List.of("Супермаркеты")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Неизвестная категория в условиях: Игры");
    }

    private RuleDefinition rule(RuleConditions conditions, RuleActions actions) {
        return new RuleDefinition("Test rule", 0.9, conditions, actions);
    }

    private FinanceOperation operation(String category, String description, String amount) {
        FinanceOperation operation = new FinanceOperation();
        operation.setCategory(category);
        operation.setDescription(description);
        operation.setOperationAmount(new BigDecimal(amount));
        operation.setOperationCurrency("RUB");
        return operation;
    }
}
