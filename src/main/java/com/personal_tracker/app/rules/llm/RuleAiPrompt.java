package com.personal_tracker.app.rules.llm;

import java.util.Collection;
import java.util.List;
import java.util.Map;

final class RuleAiPrompt {

    private RuleAiPrompt() {
    }

    static String buildUserPrompt(String userPrompt, Collection<String> categories) {
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

    static String systemPrompt() {
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

    static Map<String, Object> schema() {
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
}
