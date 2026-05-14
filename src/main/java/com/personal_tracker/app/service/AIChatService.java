package com.personal_tracker.app.service;

import com.personal_tracker.app.model.BrokerHolding;
import com.personal_tracker.app.model.FinanceOperation;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.BrokerHoldingRepository;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AIChatService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM.yyyy");

    private final FinanceOperationRepository financeOperationRepository;
    private final BrokerHoldingRepository brokerHoldingRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public AIChatService(
            FinanceOperationRepository financeOperationRepository,
            BrokerHoldingRepository brokerHoldingRepository,
            ObjectMapper objectMapper,
            @Value("${ai.lmstudio.base-url:http://localhost:1234}") String baseUrl,
            @Value("${ai.lmstudio.model:local-model}") String model,
            @Value("${ai.lmstudio.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ai.lmstudio.read-timeout-seconds:120}") long readTimeoutSeconds,
            @Value("${ai.lmstudio.max-tokens:1800}") int maxTokens
    ) {
        this.financeOperationRepository = financeOperationRepository;
        this.brokerHoldingRepository = brokerHoldingRepository;
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public List<PromptOption> getPromptOptions() {
        return List.of(
                new PromptOption("summary", "Сводка", "Коротко объясни текущее состояние денег и главные выводы."),
                new PromptOption("forecast", "Прогноз", "Сделай прогноз баланса и расходов на ближайший месяц."),
                new PromptOption("expenses", "Расходы", "Найди основные категории расходов и предложи, где можно сократить."),
                new PromptOption("broker", "Брокерский счет", "Проанализируй структуру брокерского счета и возможные риски."),
                new PromptOption("plan", "План", "Составь практичный финансовый план на 30 дней.")
        );
    }

    public ChatResponse chat(User user, ChatRequest request) {
        PromptOption prompt = getPromptOptions().stream()
                .filter(option -> option.id().equals(request.promptId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный сценарий"));

        String context = buildContext(user);
        String userMessage = """
                Сценарий: %s

                Контекст пользователя:
                %s

                Дополнительный вопрос пользователя:
                %s
                """.formatted(prompt.instruction(), context, clean(request.message()).orElse("нет"));

        Map<String, Object> payload = Map.of(
                "model", model,
                "temperature", 0.3,
                "max_tokens", maxTokens,
                "stream", false,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        Ты финансовый аналитик в приложении персонального учета.
                                        Отвечай по-русски, кратко, структурировано и практически.
                                        Укладывайся в 5-7 коротких пунктов, без длинных вступлений.
                                        Не показывай анализ запроса, ход рассуждений, черновой план или служебные пометки.
                                        Сразу выдавай готовую сводку для пользователя.
                                        Не выдумывай данных, используй только переданный контекст.
                                        Если данных мало, честно скажи, чего не хватает.
                                        Не давай гарантий доходности и не представляй ответ как индивидуальную инвестиционную рекомендацию.
                                        """
                        ),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String body = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String answer = choice.path("message").path("content").asText();
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Пустой ответ модели");
            }
            if ("length".equals(choice.path("finish_reason").asText())) {
                answer = answer.trim() + "\n\nОтвет обрезался по лимиту модели. Попробуй задать более узкий вопрос.";
            }
            return new ChatResponse(answer.trim());
        } catch (Exception exception) {
            throw new IllegalStateException("LM Studio недоступна или вернула некорректный ответ", exception);
        }
    }

    private String buildContext(User user) {
        List<FinanceOperation> operations = financeOperationRepository.findVisibleByUserIdOrderByOperationDateDesc(user.getId());
        List<BrokerHolding> holdings = brokerHoldingRepository.findByUserIdOrderByHoldingTypeAscNameAsc(user.getId());

        BigDecimal totalIncome = sum(operations.stream()
                .map(FinanceOperation::getOperationAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .toList());
        BigDecimal totalExpenses = sum(operations.stream()
                .map(FinanceOperation::getOperationAmount)
                .filter(amount -> amount != null && amount.signum() < 0)
                .map(BigDecimal::abs)
                .toList());

        LocalDate today = LocalDate.now();
        LocalDate monthAgo = today.minusDays(30);
        List<FinanceOperation> last30Days = operations.stream()
                .filter(operation -> operation.getOperationDate() != null)
                .filter(operation -> !operation.getOperationDate().toLocalDate().isBefore(monthAgo))
                .toList();

        BigDecimal last30Income = sum(last30Days.stream()
                .map(FinanceOperation::getOperationAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .toList());
        BigDecimal last30Expenses = sum(last30Days.stream()
                .map(FinanceOperation::getOperationAmount)
                .filter(amount -> amount != null && amount.signum() < 0)
                .map(BigDecimal::abs)
                .toList());

        return """
                Пользователь: %s, баланс счета: %s.
                Операций: %d. Период данных: %s.
                Всего доходов: %s, всего расходов: %s, итог: %s.
                За 30 дней: доходы %s, расходы %s, итог %s.
                Топ расходов по категориям: %s.
                Динамика по месяцам: %s.
                Последние операции: %s.
                Брокерский счет: %s.
                """.formatted(
                user.getUsername(),
                money(user.getAccountBalance()),
                operations.size(),
                operationsPeriod(operations),
                money(totalIncome),
                money(totalExpenses),
                money(totalIncome.subtract(totalExpenses)),
                money(last30Income),
                money(last30Expenses),
                money(last30Income.subtract(last30Expenses)),
                topExpenseCategories(operations),
                monthlySummary(operations),
                recentOperations(operations),
                brokerSummary(holdings)
        );
    }

    private String operationsPeriod(List<FinanceOperation> operations) {
        List<LocalDate> dates = operations.stream()
                .map(FinanceOperation::getOperationDate)
                .filter(date -> date != null)
                .map(date -> date.toLocalDate())
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return "нет дат";
        }
        return dates.get(0) + " - " + dates.get(dates.size() - 1);
    }

    private String topExpenseCategories(List<FinanceOperation> operations) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        operations.stream()
                .filter(operation -> operation.getOperationAmount() != null && operation.getOperationAmount().signum() < 0)
                .forEach(operation -> totals.merge(
                        Optional.ofNullable(operation.getCategory()).filter(category -> !category.isBlank()).orElse("Без категории"),
                        operation.getOperationAmount().abs(),
                        BigDecimal::add
                ));

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(7)
                .map(entry -> entry.getKey() + ": " + money(entry.getValue()))
                .collect(Collectors.joining("; "));
    }

    private String monthlySummary(List<FinanceOperation> operations) {
        Map<YearMonth, List<FinanceOperation>> byMonth = operations.stream()
                .filter(operation -> operation.getOperationDate() != null)
                .collect(Collectors.groupingBy(operation -> YearMonth.from(operation.getOperationDate())));

        return byMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<FinanceOperation>>comparingByKey().reversed())
                .limit(6)
                .map(entry -> {
                    BigDecimal income = sum(entry.getValue().stream()
                            .map(FinanceOperation::getOperationAmount)
                            .filter(amount -> amount != null && amount.signum() > 0)
                            .toList());
                    BigDecimal expenses = sum(entry.getValue().stream()
                            .map(FinanceOperation::getOperationAmount)
                            .filter(amount -> amount != null && amount.signum() < 0)
                            .map(BigDecimal::abs)
                            .toList());
                    return entry.getKey().format(MONTH_FORMATTER) + ": доходы " + money(income) + ", расходы " + money(expenses);
                })
                .collect(Collectors.joining("; "));
    }

    private String recentOperations(List<FinanceOperation> operations) {
        return operations.stream()
                .sorted(Comparator.comparing(FinanceOperation::getOperationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(operation -> "%s, %s, %s, %s".formatted(
                        operation.getOperationDate() == null ? "без даты" : operation.getOperationDate().toLocalDate(),
                        Optional.ofNullable(operation.getCategory()).orElse("Без категории"),
                        Optional.ofNullable(operation.getDescription()).orElse("Без описания"),
                        money(operation.getOperationAmount())
                ))
                .collect(Collectors.joining("; "));
    }

    private String brokerSummary(List<BrokerHolding> holdings) {
        if (holdings.isEmpty()) {
            return "позиций нет";
        }

        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        List<String> top = new ArrayList<>();
        holdings.forEach(holding -> {
            BigDecimal value = moneyTextToDecimal(holding.getValueText());
            byType.merge(Optional.ofNullable(holding.getHoldingType()).orElse("Активы"), value, BigDecimal::add);
            top.add("%s %s: %s, кол-во %s".formatted(
                    Optional.ofNullable(holding.getTicker()).orElse("без кода"),
                    Optional.ofNullable(holding.getName()).orElse("без названия"),
                    money(value),
                    Optional.ofNullable(holding.getQuantityText()).orElse("не указано")
            ));
        });

        String totals = byType.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + money(entry.getValue()))
                .collect(Collectors.joining("; "));
        return "итоги по типам: " + totals + ". Позиции: " + top.stream().limit(8).collect(Collectors.joining("; "));
    }

    private BigDecimal moneyTextToDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replace("₽", "").replace(" ", "").replace(",", ".").trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream()
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "0 ₽";
        }
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.toPlainString().replace(".", ",") + " ₽";
    }

    private Optional<String> clean(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public record PromptOption(String id, String title, String instruction) {
    }

    public record ChatRequest(String promptId, String message) {
    }

    public record ChatResponse(String answer) {
    }
}
