package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.rules.dto.ParsedRuleResponse;
import com.personal_tracker.app.rules.llm.RuleAiClient;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.repository.RuleApplicationRepository;
import com.personal_tracker.app.rules.repository.RuleRepository;
import com.personal_tracker.app.service.FinanceOperationService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleParsingIntegrationTest {

    private static final Long USER_ID = 10L;
    private static final List<String> CATEGORIES = List.of(
            "Аптеки",
            "Медицина",
            "Здоровье",
            "Фастфуд",
            "Маркетплейсы",
            "Супермаркеты",
            "Переводы",
            "Наличные"
    );

    @Test
    void parsesHealthCategoryRule() {
        RuleDefinition rule = parse("объедини расходы из аптек и медицины в здоровье");

        assertThat(rule.conditions().type()).isEqualTo("expense");
        assertThat(rule.conditions().categoryIn()).containsExactlyInAnyOrder("Аптеки", "Медицина");
        assertThat(rule.actions().setCategory()).isEqualTo("Здоровье");
    }

    @Test
    void parsesFastFoodDescriptionRule() {
        RuleDefinition rule = parse("burger king и kfc отнеси к фастфуду");

        assertThat(rule.conditions().descriptionContains())
                .anySatisfy(value -> assertThat(value).containsIgnoringCase("burger"))
                .anySatisfy(value -> assertThat(value).containsIgnoringCase("kfc"));
        assertThat(rule.actions().setCategory()).isEqualTo("Фастфуд");
    }

    @Test
    void parsesCategoryRenameAsCategoryChange() {
        RuleDefinition rule = parse("переименовать супермаркеты в магазины");

        assertThat(rule.conditions().categoryIn()).containsExactly("Супермаркеты");
        assertThat(rule.actions().setCategory()).isEqualTo("Магазины");
        assertThat(rule.actions().renameDescription()).isNull();
        assertThat(rule.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void parsesInvestJarExclusionRule() {
        RuleDefinition rule = parse("исключи из статистики операции с инвесткопилкой");

        assertThat(rule.actions().excludeFromAnalytics()).isTrue();
        assertThat(textConditions(rule))
                .anySatisfy(value -> assertThat(value).containsIgnoringCase("инвест"));
    }

    private List<String> textConditions(RuleDefinition rule) {
        List<String> values = new ArrayList<>();
        values.addAll(rule.conditions().descriptionContains());
        values.addAll(rule.conditions().counterpartyContains());
        return values;
    }

    private RuleDefinition parse(String prompt) {
        ParsedRuleResponse response = ruleService().parse(user(), prompt);
        return response.rule();
    }

    private RuleService ruleService() {
        String apiKey = openAiApiKey()
                .filter(value -> !value.isBlank())
                .orElse(null);
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_OPENAI_RULE_TESTS")),
                "AI parsing tests are skipped. Set RUN_OPENAI_RULE_TESTS=true to run them."
        );
        Assumptions.assumeTrue(apiKey != null, "OPENAI_API_KEY is not configured");

        FinanceOperationRepository financeOperationRepository = mock(FinanceOperationRepository.class);
        when(financeOperationRepository.findVisibleCategoriesByUserId(USER_ID)).thenReturn(CATEGORIES);

        return new RuleService(
                new RuleAiClient(apiKey, "https://api.openai.com/v1/responses", "gpt-5-nano", 500),
                new RuleValidator(),
                mock(RulePreviewService.class),
                new RuleEngine(),
                mock(RuleRepository.class),
                mock(RuleApplicationRepository.class),
                financeOperationRepository,
                mock(FinanceOperationService.class)
        );
    }

    private User user() {
        User user = new User("demo", "demo@example.com", "password");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Optional<String> openAiApiKey() {
        String value = System.getenv("OPENAI_API_KEY");
        if (value != null && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        try {
            return Files.readAllLines(Path.of(".env")).stream()
                    .filter(line -> line.startsWith("OPENAI_API_KEY="))
                    .map(line -> line.substring("OPENAI_API_KEY=".length()).trim())
                    .filter(line -> !line.isBlank())
                    .findFirst();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
