package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.entity.RuleApplicationEntity;
import com.personal_tracker.app.rules.entity.RuleEntity;
import com.personal_tracker.app.rules.llm.RuleAiClient;
import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.repository.RuleApplicationRepository;
import com.personal_tracker.app.rules.repository.RuleRepository;
import com.personal_tracker.app.service.FinanceOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleServiceTest {

    private final RuleAiClient ruleAiClient = mock(RuleAiClient.class);
    private final RuleValidator ruleValidator = new RuleValidator();
    private final RulePreviewService rulePreviewService = mock(RulePreviewService.class);
    private final RuleEngine ruleEngine = new RuleEngine();
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final RuleApplicationRepository ruleApplicationRepository = mock(RuleApplicationRepository.class);
    private final FinanceOperationRepository financeOperationRepository = mock(FinanceOperationRepository.class);
    private final FinanceOperationService financeOperationService = mock(FinanceOperationService.class);
    private final RuleService ruleService = new RuleService(
            ruleAiClient,
            ruleValidator,
            rulePreviewService,
            ruleEngine,
            ruleRepository,
            ruleApplicationRepository,
            financeOperationRepository,
            financeOperationService
    );

    @Test
    void parseUsesUserCategoriesAndCanonicalizesAiCategoryAliases() {
        User user = user(10L);
        RuleDefinition aiRule = new RuleDefinition(
                "  Транспортные траты  ",
                0.91,
                new RuleConditions(List.of(), List.of("жд билеты"), "expense", null, null, List.of()),
                new RuleActions("траспорт", false, null, false, null)
        );

        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Ж/д билеты", "Транспорт", "Маркетплейсы"));
        when(ruleAiClient.parse(eq("жд билеты в транспорт"), isA(Collection.class))).thenReturn(aiRule);

        RuleDefinition parsedRule = ruleService.parse(user, "  жд билеты в транспорт  ").rule();

        assertThat(parsedRule.name()).isEqualTo("Транспортные траты");
        assertThat(parsedRule.conditions().categoryIn()).containsExactly("Ж/д билеты");
        assertThat(parsedRule.actions().setCategory()).isEqualTo("Транспорт");
        verify(ruleAiClient).parse(
                eq("жд билеты в транспорт"),
                argThat(categories -> List.copyOf(categories).equals(List.of("Ж/д билеты", "Транспорт", "Маркетплейсы")))
        );
    }

    @Test
    void parseAllowsNewTargetCategoryFromUserPrompt() {
        User user = user(10L);
        RuleDefinition aiRule = new RuleDefinition(
                "Подписки",
                0.9,
                new RuleConditions(List.of("plus"), List.of(), "expense", null, null, List.of()),
                new RuleActions("Подписки", false, null, false, null)
        );

        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Маркетплейсы", "Супермаркеты"));
        when(ruleAiClient.parse(eq("яндекс плюс в подписки"), isA(Collection.class))).thenReturn(aiRule);

        RuleDefinition parsedRule = ruleService.parse(user, "яндекс плюс в подписки").rule();

        assertThat(parsedRule.actions().setCategory()).isEqualTo("Подписки");
    }

    @Test
    void parseRejectsRuleWithoutConditions() {
        User user = user(10L);
        RuleDefinition aiRule = new RuleDefinition(
                "Пустое правило",
                0.9,
                new RuleConditions(List.of(), List.of(), "all", null, null, List.of()),
                new RuleActions("Маркетплейсы", false, null, false, null)
        );

        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Маркетплейсы"));
        when(ruleAiClient.parse(eq("сделай маркетплейсы"), isA(Collection.class))).thenReturn(aiRule);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ruleService.parse(user, "сделай маркетплейсы"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("хотя бы одно условие");
    }

    @Test
    void parseCategoryRenameOverridesUnstableAiResult() {
        User user = user(10L);
        RuleDefinition unstableAiRule = new RuleDefinition(
                "",
                0.6,
                new RuleConditions(List.of("супермаркеты"), List.of(), "all", null, null, List.of()),
                new RuleActions(null, false, null, false, "Маркетплейсы")
        );

        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Супермаркеты", "Маркетплейсы"));
        when(ruleAiClient.parse(eq("переименовать супермаркеты в магазины"), isA(Collection.class))).thenReturn(unstableAiRule);

        RuleDefinition parsedRule = ruleService.parse(user, "переименовать супермаркеты в магазины").rule();

        assertThat(parsedRule.name()).isEqualTo("Новое правило");
        assertThat(parsedRule.confidence()).isGreaterThanOrEqualTo(0.95);
        assertThat(parsedRule.conditions().categoryIn()).containsExactly("Супермаркеты");
        assertThat(parsedRule.actions().setCategory()).isEqualTo("Магазины");
        assertThat(parsedRule.actions().renameDescription()).isNull();
    }

    @Test
    void disablingRuleRollsBackOnlyOperationsStillChangedByThisRule() {
        User user = user(10L);
        RuleEntity rule = savedRule(user);
        FinanceOperation untouchedAfterRule = operation("Маркетплейсы", "Ozon");
        FinanceOperation editedAfterRule = operation("Подарки", "Ozon");
        RuleApplicationEntity firstApplication = application(rule, untouchedAfterRule);
        RuleApplicationEntity secondApplication = application(rule, editedAfterRule);

        when(ruleRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(rule)).thenReturn(rule);
        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Супермаркеты", "Маркетплейсы"));
        when(ruleApplicationRepository.findByRuleId(7L))
                .thenReturn(List.of(firstApplication, secondApplication));
        when(rulePreviewService.preview(10L, definition())).thenReturn(new RulePreviewResponse(0, List.of()));

        ruleService.setEnabled(user, 7L, false);

        assertThat(rule.isEnabled()).isFalse();
        assertThat(untouchedAfterRule.getCategory()).isEqualTo("Супермаркеты");
        assertThat(editedAfterRule.getCategory()).isEqualTo("Подарки");
        verify(financeOperationRepository).saveAll(List.of(untouchedAfterRule));
        verify(ruleApplicationRepository).deleteByRuleId(7L);
    }

    @Test
    void enablingRuleAppliesItAgainAndStoresNewApplications() {
        User user = user(10L);
        RuleEntity rule = savedRule(user);
        rule.setEnabled(false);
        FinanceOperation operation = operation("Супермаркеты", "Ozon");

        when(ruleRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(rule)).thenReturn(rule);
        when(financeOperationRepository.findVisibleCategoriesByUserId(10L))
                .thenReturn(List.of("Супермаркеты", "Маркетплейсы"));
        when(rulePreviewService.affectedOperations(10L, definition())).thenReturn(List.of(operation));
        when(rulePreviewService.preview(10L, definition())).thenReturn(new RulePreviewResponse(1, List.of()));

        ruleService.setEnabled(user, 7L, true);

        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getLastAppliedCount()).isEqualTo(1);
        assertThat(operation.getCategory()).isEqualTo("Маркетплейсы");
        verify(financeOperationRepository).saveAll(List.of(operation));
        verify(ruleApplicationRepository).saveAll(anyList());
    }

    private User user(Long id) {
        User user = new User("demo", "demo@example.com", "password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RuleEntity savedRule(User user) {
        RuleEntity rule = new RuleEntity();
        ReflectionTestUtils.setField(rule, "id", 7L);
        rule.setUser(user);
        rule.setName("Ozon в маркетплейсы");
        rule.setConditionsJson("""
                {"descriptionContains":["Ozon"],"categoryIn":["Супермаркеты"],"type":"all","amountMin":null,"amountMax":null,"counterpartyContains":[]}
                """);
        rule.setActionsJson("""
                {"setCategory":"Маркетплейсы","excludeFromAnalytics":false,"setCounterparty":null,"markAsTransfer":false,"renameDescription":null}
                """);
        rule.setEnabled(true);
        rule.setLastAppliedCount(1);
        return rule;
    }

    private RuleDefinition definition() {
        return new RuleDefinition(
                "Ozon в маркетплейсы",
                1,
                new RuleConditions(List.of("Ozon"), List.of("Супермаркеты"), "all", null, null, List.of()),
                new RuleActions("Маркетплейсы", false, null, false, null)
        );
    }

    private RuleApplicationEntity application(RuleEntity rule, FinanceOperation operation) {
        RuleApplicationEntity application = new RuleApplicationEntity();
        application.setRule(rule);
        application.setOperation(operation);
        application.setBeforeCategory("Супермаркеты");
        application.setBeforeExcludeFromAnalytics(false);
        application.setBeforeDescription("Ozon");
        application.setAfterCategory("Маркетплейсы");
        application.setAfterExcludeFromAnalytics(false);
        application.setAfterDescription("Ozon");
        return application;
    }

    private FinanceOperation operation(String category, String description) {
        FinanceOperation operation = new FinanceOperation();
        operation.setCategory(category);
        operation.setDescription(description);
        return operation;
    }
}
