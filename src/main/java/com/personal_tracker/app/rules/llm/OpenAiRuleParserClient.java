package com.personal_tracker.app.rules.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.personal_tracker.app.rules.model.RuleDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiRuleParserClient implements RuleParserClient {

    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final double FALLBACK_CONFIDENCE_THRESHOLD = 0.75;
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String primaryModel;
    private final String fallbackModel;
    private final ThreadLocal<Boolean> usedFallback = ThreadLocal.withInitial(() -> false);

    public OpenAiRuleParserClient(
            @Value("${openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${rules.llm.primary-model:gpt-5-nano}") String primaryModel,
            @Value("${rules.llm.fallback-model:gpt-5-mini}") String fallbackModel
    ) {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public RuleDefinition parse(String userPrompt, Collection<String> categories) {
        usedFallback.set(false);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key не настроен");
        }

        RuleDefinition primaryRule;
        try {
            primaryRule = parseWithModel(primaryModel, userPrompt, categories);
        } catch (RuntimeException primaryException) {
            RuleDefinition fallbackRule = parseWithFallbackModel(userPrompt, categories);
            usedFallback.set(true);
            return fallbackRule;
        }

        if (primaryRule.confidence() >= FALLBACK_CONFIDENCE_THRESHOLD) {
            return primaryRule;
        }

        try {
            RuleDefinition fallbackRule = parseWithModel(fallbackModel, userPrompt, categories);
            if (fallbackRule.confidence() > primaryRule.confidence()) {
                usedFallback.set(true);
                return fallbackRule;
            }
        } catch (RuntimeException ignored) {
            // A valid primary result is still better than failing the request after fallback.
        }

        return primaryRule;
    }

    @Override
    public boolean usedFallback() {
        return usedFallback.get();
    }

    private RuleDefinition parseWithFallbackModel(String userPrompt, Collection<String> categories) {
        try {
            return parseWithModel(fallbackModel, userPrompt, categories);
        } catch (RuntimeException fallbackException) {
            throw fallbackException;
        }
    }

    private RuleDefinition parseWithModel(String model, String userPrompt, Collection<String> categories) {
        try {
            String responseBody = sendRequest(model, buildUserPrompt(userPrompt, categories));
            JsonNode root = objectMapper.readTree(responseBody);
            validateResponseStatus(root);
            String jsonText = extractOutputText(root);
            RuleDefinition rule = readRuleDefinition(jsonText);
            validateRule(rule);
            return rule;
        } catch (IOException exception) {
            throw new IllegalArgumentException("LLM вернул невалидный JSON", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к LLM прерван", exception);
        }
    }

    private String sendRequest(String model, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", userPrompt
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "finance_rule",
                                "strict", true,
                                "schema", schema()
                        )
                ),
                "store", false
        );

        HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenAiApiException("OpenAI API вернул ошибку: "
                    + response.statusCode()
                    + ". "
                    + extractErrorMessage(response.body()));
        }
        return response.body();
    }

    private void validateResponseStatus(JsonNode root) {
        String status = root.path("status").asText("");
        if ("failed".equals(status) || "incomplete".equals(status)) {
            throw new OpenAiApiException("OpenAI response status: " + status + ". " + responseError(root));
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new OpenAiApiException("OpenAI response error: " + responseError(root));
        }
    }

    private String responseError(JsonNode root) {
        JsonNode message = root.path("error").path("message");
        if (message.isTextual() && !message.asText().isBlank()) {
            return limit(message.asText());
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            return limit(error.toString());
        }
        return "";
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode message = objectMapper.readTree(responseBody).at("/error/message");
            if (message.isTextual()) {
                return limit(message.asText());
            }
        } catch (IOException ignored) {
            // Fall back to a compact raw body below.
        }
        return limit(responseBody);
    }

    private String extractOutputText(JsonNode root) {
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    if (!"output_text".equals(contentItem.path("type").asText())) {
                        continue;
                    }
                    JsonNode text = contentItem.path("text");
                    if (text.isTextual() && !text.asText().isBlank()) {
                        return text.asText();
                    }
                }
            }
        }
        throw new IllegalArgumentException("OpenAI API не вернул JSON");
    }

    private RuleDefinition readRuleDefinition(String jsonText) throws IOException {
        JsonNode ruleNode = objectMapper.readTree(jsonText);
        JsonNode confidence = ruleNode.path("confidence");
        if (!confidence.isNumber()) {
            throw new IllegalArgumentException("LLM не вернул confidence");
        }
        return objectMapper.treeToValue(ruleNode, RuleDefinition.class);
    }

    private void validateRule(RuleDefinition rule) {
        if (rule == null) {
            throw new IllegalArgumentException("LLM не вернул правило");
        }
        if (Double.isNaN(rule.confidence())) {
            throw new IllegalArgumentException("LLM вернул некорректный confidence");
        }
    }

    private String buildUserPrompt(String userPrompt, Collection<String> categories) {
        return """
                Allowed categories:
                %s

                User prompt:
                %s

                Mapping hints:
                - "не включай в статистику", "не учитывать", "исключи из аналитики" => actions.excludeFromAnalytics=true.
                - Existing source category names must go to conditions.categoryIn, not descriptionContains.
                - Merchant names, brands, counterparties, and free text must go to conditions.descriptionContains.
                - For "A в B", if A is an allowed category, use categoryIn [A] and setCategory B.
                - For "установить категорию B для A, C, D", if A/C/D are allowed categories, use categoryIn [A, C, D] and setCategory B.
                - Do not invent categories. Use only allowed categories for categoryIn and setCategory.

                Examples:
                Input: Экосистема Яндекс в Цифровые товары
                Output category logic: categoryIn ["Экосистема Яндекс"], setCategory "Цифровые товары".

                Input: Установить категорию Транспорт для ЖД билетов, Авиабилетов, Такси и Местного транспорта
                Output category logic: categoryIn ["ЖД билеты", "Авиабилеты", "Такси", "Местный транспорт"], type "expense", setCategory "Транспорт".

                Input: переименовать Yandex Plus в Подписки
                Output rename logic: descriptionContains ["Yandex Plus"], renameDescription "Подписки".

                Input: не включай пополнение и снятие средств с брокерского счета в статистику
                Output exclude logic: descriptionContains ["брокерский счет", "брокерского счета", "брокер"], excludeFromAnalytics true.
                """.formatted(
                categories == null ? "" : String.join(", ", categories),
                userPrompt == null ? "" : userPrompt.trim()
        );
    }

    private String systemPrompt() {
        return """
                Ты parser финансовых правил.
                Возвращай только JSON по schema.
                Не используй markdown, комментарии или пояснения.
                Не выдумывай категории.
                Категории выбирай только из allowed categories.
                "не включай в статистику", "не учитывать", "исключи из аналитики" => excludeFromAnalytics=true.
                Source category names должны идти в categoryIn, а не descriptionContains.
                Merchant/free text должны идти в descriptionContains.
                """;
    }

    private Map<String, Object> schema() {
        Map<String, Object> stringArray = Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("name", "confidence", "conditions", "actions"),
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "confidence", Map.of("type", "number"),
                        "conditions", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of(
                                        "descriptionContains",
                                        "categoryIn",
                                        "type",
                                        "amountMin",
                                        "amountMax",
                                        "counterpartyContains"
                                ),
                                "properties", Map.of(
                                        "descriptionContains", stringArray,
                                        "categoryIn", stringArray,
                                        "type", Map.of("type", "string", "enum", List.of("income", "expense", "all")),
                                        "amountMin", Map.of("type", List.of("number", "null")),
                                        "amountMax", Map.of("type", List.of("number", "null")),
                                        "counterpartyContains", stringArray
                                )
                        ),
                        "actions", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of(
                                        "setCategory",
                                        "excludeFromAnalytics",
                                        "setCounterparty",
                                        "markAsTransfer",
                                        "renameDescription"
                                ),
                                "properties", Map.of(
                                        "setCategory", Map.of("type", List.of("string", "null")),
                                        "excludeFromAnalytics", Map.of("type", "boolean"),
                                        "setCounterparty", Map.of("type", List.of("string", "null")),
                                        "markAsTransfer", Map.of("type", "boolean"),
                                        "renameDescription", Map.of("type", List.of("string", "null"))
                                )
                        )
                )
        );
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_ERROR_BODY_LENGTH ? value.substring(0, MAX_ERROR_BODY_LENGTH) : value;
    }
}
