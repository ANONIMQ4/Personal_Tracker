package com.personal_tracker.app.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.personal_tracker.app.model.FinanceOperation;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.rules.dto.ApplyRuleRequest;
import com.personal_tracker.app.rules.dto.ParsedRuleResponse;
import com.personal_tracker.app.rules.dto.RuleDto;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.entity.RuleEntity;
import com.personal_tracker.app.rules.llm.RuleParserClient;
import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.repository.RuleRepository;
import com.personal_tracker.app.service.FinanceOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
public class RuleService {

    private final RuleParserClient ruleParserClient;
    private final RuleValidator ruleValidator;
    private final RulePreviewService rulePreviewService;
    private final RuleEngine ruleEngine;
    private final RuleRepository ruleRepository;
    private final FinanceOperationRepository financeOperationRepository;
    private final FinanceOperationService financeOperationService;
    private final ObjectMapper objectMapper;

    public RuleService(
            RuleParserClient ruleParserClient,
            RuleValidator ruleValidator,
            RulePreviewService rulePreviewService,
            RuleEngine ruleEngine,
            RuleRepository ruleRepository,
            FinanceOperationRepository financeOperationRepository,
            FinanceOperationService financeOperationService
    ) {
        this.ruleParserClient = ruleParserClient;
        this.ruleValidator = ruleValidator;
        this.rulePreviewService = rulePreviewService;
        this.ruleEngine = ruleEngine;
        this.ruleRepository = ruleRepository;
        this.financeOperationRepository = financeOperationRepository;
        this.financeOperationService = financeOperationService;
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    public ParsedRuleResponse parse(User user, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Опиши правило");
        }
        if (prompt.length() > 1_000) {
            throw new IllegalArgumentException("Правило слишком длинное");
        }

        Collection<String> categories = allowedCategories(user.getId());
        RuleDefinition parsedRule = ruleParserClient.parse(prompt.trim(), categories);
        RuleDefinition rule = normalizeRule(parsedRule, categories);
        ruleValidator.validate(rule, categories);

        List<String> warnings = new ArrayList<>();
        if (ruleParserClient.usedFallback()) {
            warnings.add("Использован fallback parser");
        }
        if (rule.confidence() < 0.85) {
            warnings.add("Проверь правило особенно внимательно: уверенность ниже 0.85");
        }
        return new ParsedRuleResponse(rule, warnings, ruleParserClient.usedFallback());
    }

    public RulePreviewResponse preview(User user, RuleDefinition rule) {
        Collection<String> categories = allowedCategories(user.getId());
        RuleDefinition normalizedRule = normalizeRule(rule, categories);
        ruleValidator.validate(normalizedRule, categories);
        return rulePreviewService.preview(user.getId(), normalizedRule);
    }

    @Transactional
    public RulePreviewResponse apply(User user, ApplyRuleRequest request) {
        Collection<String> categories = allowedCategories(user.getId());
        RuleDefinition rule = normalizeRule(request.rule(), categories);
        ruleValidator.validate(rule, categories);
        RulePreviewResponse preview = rulePreviewService.preview(user.getId(), rule);
        List<FinanceOperation> operations = rulePreviewService.affectedOperations(user.getId(), rule);
        operations.forEach(operation -> {
            ruleEngine.apply(operation, rule);
            financeOperationService.refreshOperationKey(user.getId(), operation);
        });
        financeOperationRepository.saveAll(operations);
        if (request.saveRule()) {
            saveRule(user, rule, request.originalPrompt(), preview.affectedCount());
        }
        return preview;
    }

    public List<RuleDto> getRules(User user) {
        Collection<String> categories = allowedCategories(user.getId());
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(entity -> {
                    RuleDefinition rule = toDefinition(entity, categories);
                    return toDto(entity, rule, rulePreviewService.preview(user.getId(), rule).affectedCount());
                })
                .toList();
    }

    @Transactional
    public RuleDto setEnabled(User user, Long ruleId, boolean enabled) {
        RuleEntity entity = ruleRepository.findByIdAndUserId(ruleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Правило не найдено"));
        entity.setEnabled(enabled);
        RuleEntity savedEntity = ruleRepository.save(entity);
        Collection<String> categories = allowedCategories(user.getId());
        RuleDefinition rule = toDefinition(savedEntity, categories);
        return toDto(savedEntity, rule, rulePreviewService.preview(user.getId(), rule).affectedCount());
    }

    @Transactional
    public void delete(User user, Long ruleId) {
        RuleEntity entity = ruleRepository.findByIdAndUserId(ruleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Правило не найдено"));
        ruleRepository.delete(entity);
    }

    private RuleEntity saveRule(User user, RuleDefinition rule, String originalPrompt, long lastAppliedCount) {
        try {
            RuleEntity entity = new RuleEntity();
            entity.setUser(user);
            entity.setName(rule.name());
            entity.setOriginalPrompt(originalPrompt);
            entity.setConditionsJson(objectMapper.writeValueAsString(rule.conditions()));
            entity.setActionsJson(objectMapper.writeValueAsString(rule.actions()));
            entity.setEnabled(true);
            entity.setLastAppliedCount(lastAppliedCount);
            return ruleRepository.save(entity);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Не удалось сохранить правило", exception);
        }
    }

    private RuleDto toDto(RuleEntity entity, RuleDefinition rule, long affectedCount) {
        return new RuleDto(
                entity.getId(),
                entity.getName(),
                entity.getOriginalPrompt(),
                rule,
                entity.isEnabled(),
                affectedCount,
                entity.getLastAppliedCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RuleDefinition toDefinition(RuleEntity entity, Collection<String> allowedCategories) {
        try {
            return normalizeRule(new RuleDefinition(
                    entity.getName(),
                    1,
                    objectMapper.readValue(entity.getConditionsJson(), RuleConditions.class),
                    objectMapper.readValue(entity.getActionsJson(), RuleActions.class)
            ), allowedCategories);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Правило повреждено", exception);
        }
    }

    private Set<String> allowedCategories(Long userId) {
        List<String> userCategories = financeOperationRepository.findVisibleByUserIdOrderByOperationDateDesc(userId).stream()
                .map(operation -> operation.getCategory() == null ? "Без категории" : operation.getCategory())
                .toList();
        return ruleValidator.allowedCategories(userCategories);
    }

    private RuleDefinition normalizeRule(RuleDefinition rule, Collection<String> allowedCategories) {
        if (rule == null) {
            return null;
        }
        RuleConditions conditions = rule.conditions();
        RuleActions actions = rule.actions();
        return new RuleDefinition(
                rule.name() == null ? "Новое правило" : rule.name().trim(),
                rule.confidence(),
                conditions == null ? null : new RuleConditions(
                        cleanList(conditions.descriptionContains()),
                        normalizeCategories(conditions.categoryIn(), allowedCategories),
                        conditions.type(),
                        conditions.amountMin(),
                        conditions.amountMax(),
                        cleanList(conditions.counterpartyContains())
                ),
                actions == null ? null : new RuleActions(
                        normalizeCategory(actions.setCategory(), allowedCategories),
                        actions.excludeFromAnalytics(),
                        cleanString(actions.setCounterparty()),
                        actions.markAsTransfer(),
                        cleanString(actions.renameDescription())
                )
        );
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::cleanString)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> normalizeCategories(List<String> values, Collection<String> allowedCategories) {
        return cleanList(values).stream()
                .map(value -> normalizeCategory(value, allowedCategories))
                .toList();
    }

    private String normalizeCategory(String value, Collection<String> allowedCategories) {
        return RuleCategoryMatcher.canonicalize(value, allowedCategories);
    }

    private String cleanString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
