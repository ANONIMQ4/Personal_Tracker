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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class RuleAiClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI responsesUri;
    private final String apiKey;
    private final String model;
    private final int maxErrorBodyLength;

    public RuleAiClient(
            @Value("${openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${openai.responses-uri:https://api.openai.com/v1/responses}") String responsesUri,
            @Value("${rules.llm.model:${RULES_LLM_MODEL:gpt-5-nano}}") String model,
            @Value("${rules.llm.max-error-body-length:500}") int maxErrorBodyLength
    ) {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.responsesUri = URI.create(responsesUri);
        this.apiKey = apiKey;
        this.model = model;
        this.maxErrorBodyLength = maxErrorBodyLength;
    }

    public RuleDefinition parse(String userPrompt, Collection<String> categories) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key не настроен");
        }
        return parseWithModel(model, userPrompt, categories);
    }

    private RuleDefinition parseWithModel(String model, String userPrompt, Collection<String> categories) {
        try {
            String responseBody = sendRequest(model, RuleAiPrompt.buildUserPrompt(userPrompt, categories));
            JsonNode root = objectMapper.readTree(responseBody);
            validateResponseStatus(root);
            String jsonText = extractOutputText(root);
            RuleDefinition rule = readRuleDefinition(jsonText);
            validateRule(rule);
            return rule;
        } catch (HttpTimeoutException exception) {
            throw new IllegalStateException("OpenAI API не ответил за 60 секунд", exception);
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
                                "content", RuleAiPrompt.systemPrompt()
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
                                "schema", RuleAiPrompt.schema()
                        )
                ),
                "store", false
        );

        HttpRequest request = HttpRequest.newBuilder(responsesUri)
                .timeout(Duration.ofSeconds(60))
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

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > maxErrorBodyLength ? value.substring(0, maxErrorBodyLength) : value;
    }

    private static class OpenAiApiException extends RuntimeException {

        OpenAiApiException(String message) {
            super(message);
        }
    }
}
