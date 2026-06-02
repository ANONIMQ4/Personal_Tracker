package com.personal_tracker.app.rules.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.rules.dto.ApplyRuleRequest;
import com.personal_tracker.app.rules.dto.ParsedRuleResponse;
import com.personal_tracker.app.rules.dto.RuleDto;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.dto.RulePreviewResponse.OperationState;
import com.personal_tracker.app.rules.entity.RuleApplicationEntity;
import com.personal_tracker.app.rules.entity.RuleEntity;
import com.personal_tracker.app.rules.llm.RuleAiClient;
import com.personal_tracker.app.rules.model.RuleActions;
import com.personal_tracker.app.rules.model.RuleConditions;
import com.personal_tracker.app.rules.model.RuleDefinition;
import com.personal_tracker.app.rules.repository.RuleApplicationRepository;
import com.personal_tracker.app.rules.repository.RuleRepository;
import com.personal_tracker.app.service.FinanceOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleService {

    private static final Pattern CATEGORY_RENAME_PATTERN = Pattern.compile(
            "^\\s*(?:переименовать|переименуй)\\s+(.+?)\\s+в\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final RuleAiClient ruleParserClient;
    private final RuleValidator ruleValidator;
    private final RulePreviewService rulePreviewService;
    private final RuleEngine ruleEngine;
    private final RuleRepository ruleRepository;
    private final RuleApplicationRepository ruleApplicationRepository;
    private final FinanceOperationRepository financeOperationRepository;
    private final FinanceOperationService financeOperationService;
    private final ObjectMapper objectMapper;

    public RuleService(
            RuleAiClient ruleParserClient,
            RuleValidator ruleValidator,
            RulePreviewService rulePreviewService,
            RuleEngine ruleEngine,
            RuleRepository ruleRepository,
            RuleApplicationRepository ruleApplicationRepository,
            FinanceOperationRepository financeOperationRepository,
            FinanceOperationService financeOperationService
    ) {
        this.ruleParserClient = ruleParserClient;
        this.ruleValidator = ruleValidator;
        this.rulePreviewService = rulePreviewService;
        this.ruleEngine = ruleEngine;
        this.ruleRepository = ruleRepository;
        this.ruleApplicationRepository = ruleApplicationRepository;
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
        Optional<CategoryRenameIntent> renameIntent = categoryRenameIntent(prompt.trim(), categories);
        RuleDefinition parsedRule = ruleParserClient.parse(prompt.trim(), categories);
        RuleDefinition rule = normalizeRule(applyCategoryRenameIntent(parsedRule, renameIntent), categories);
        ruleValidator.validate(rule, categories);

        List<String> warnings = new ArrayList<>();
        if (rule.confidence() < 0.85) {
            warnings.add("Проверь правило особенно внимательно: уверенность ниже 0.85");
        }
        return new ParsedRuleResponse(rule, warnings);
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
        RuleEntity savedRule = request.saveRule()
                ? saveRule(user, rule, request.originalPrompt(), preview.affectedCount())
                : null;
        List<RuleApplicationEntity> applications = applyToOperations(user.getId(), savedRule, rule, operations);
        if (savedRule != null) {
            savedRule.setLastAppliedCount(applications.size());
            ruleRepository.save(savedRule);
        }
        financeOperationRepository.saveAll(operations);
        if (!applications.isEmpty()) {
            ruleApplicationRepository.saveAll(applications);
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
        if (entity.isEnabled() == enabled) {
            Collection<String> categories = allowedCategories(user.getId());
            RuleDefinition rule = toDefinition(entity, categories);
            return toDto(entity, rule, rulePreviewService.preview(user.getId(), rule).affectedCount());
        }

        Collection<String> categories = allowedCategories(user.getId());
        RuleDefinition rule = toDefinition(entity, categories);
        if (enabled) {
            enableRule(user.getId(), entity, rule);
        } else {
            rollbackRule(user.getId(), entity);
        }
        entity.setEnabled(enabled);
        RuleEntity savedEntity = ruleRepository.save(entity);
        return toDto(savedEntity, rule, rulePreviewService.preview(user.getId(), rule).affectedCount());
    }

    @Transactional
    public void delete(User user, Long ruleId) {
        RuleEntity entity = ruleRepository.findByIdAndUserId(ruleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Правило не найдено"));
        rollbackRule(user.getId(), entity);
        ruleRepository.delete(entity);
    }

    private void enableRule(Long userId, RuleEntity entity, RuleDefinition rule) {
        List<FinanceOperation> operations = rulePreviewService.affectedOperations(userId, rule);
        List<RuleApplicationEntity> applications = applyToOperations(userId, entity, rule, operations);
        entity.setLastAppliedCount(applications.size());
        financeOperationRepository.saveAll(operations);
        if (!applications.isEmpty()) {
            ruleApplicationRepository.saveAll(applications);
        }
    }

    private List<RuleApplicationEntity> applyToOperations(
            Long userId,
            RuleEntity savedRule,
            RuleDefinition rule,
            List<FinanceOperation> operations
    ) {
        List<RuleApplicationEntity> applications = new ArrayList<>();
        for (FinanceOperation operation : operations) {
            OperationState before = ruleEngine.stateOf(operation);
            ruleEngine.apply(operation, rule);
            OperationState after = ruleEngine.stateOf(operation);
            financeOperationService.refreshOperationKey(userId, operation);
            if (savedRule != null && !sameState(before, after)) {
                applications.add(application(savedRule, operation, before, after));
            }
        }
        return applications;
    }

    private void rollbackRule(Long userId, RuleEntity entity) {
        List<RuleApplicationEntity> applications = ruleApplicationRepository.findByRuleId(entity.getId());
        List<FinanceOperation> operationsToSave = new ArrayList<>();
        for (RuleApplicationEntity application : applications) {
            FinanceOperation operation = application.getOperation();
            if (sameState(ruleEngine.stateOf(operation), afterState(application))) {
                ruleEngine.restore(operation, beforeState(application));
                financeOperationService.refreshOperationKey(userId, operation);
                operationsToSave.add(operation);
            }
        }
        financeOperationRepository.saveAll(operationsToSave);
        ruleApplicationRepository.deleteByRuleId(entity.getId());
        entity.setLastAppliedCount(0);
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

    private RuleApplicationEntity application(
            RuleEntity rule,
            FinanceOperation operation,
            OperationState before,
            OperationState after
    ) {
        RuleApplicationEntity application = new RuleApplicationEntity();
        application.setRule(rule);
        application.setOperation(operation);
        application.setBeforeCategory(before.category());
        application.setBeforeExcludeFromAnalytics(before.excludeFromAnalytics());
        application.setBeforeCounterparty(before.counterparty());
        application.setBeforeDescription(before.description());
        application.setAfterCategory(after.category());
        application.setAfterExcludeFromAnalytics(after.excludeFromAnalytics());
        application.setAfterCounterparty(after.counterparty());
        application.setAfterDescription(after.description());
        return application;
    }

    private OperationState beforeState(RuleApplicationEntity application) {
        return new OperationState(
                application.getBeforeCategory(),
                application.isBeforeExcludeFromAnalytics(),
                application.getBeforeCounterparty(),
                application.getBeforeDescription()
        );
    }

    private OperationState afterState(RuleApplicationEntity application) {
        return new OperationState(
                application.getAfterCategory(),
                application.isAfterExcludeFromAnalytics(),
                application.getAfterCounterparty(),
                application.getAfterDescription()
        );
    }

    private boolean sameState(OperationState first, OperationState second) {
        return first.excludeFromAnalytics() == second.excludeFromAnalytics()
                && java.util.Objects.equals(first.category(), second.category())
                && java.util.Objects.equals(first.counterparty(), second.counterparty())
                && java.util.Objects.equals(first.description(), second.description());
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
        return ruleValidator.allowedCategories(financeOperationRepository.findVisibleCategoriesByUserId(userId));
    }

    private RuleDefinition normalizeRule(RuleDefinition rule, Collection<String> allowedCategories) {
        if (rule == null) {
            return null;
        }
        RuleConditions conditions = rule.conditions();
        RuleActions actions = rule.actions();
        return new RuleDefinition(
                cleanName(rule.name()),
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

    private String cleanName(String value) {
        return value == null || value.isBlank() ? "Новое правило" : value.trim();
    }

    private RuleDefinition applyCategoryRenameIntent(RuleDefinition rule, Optional<CategoryRenameIntent> intent) {
        if (intent.isEmpty()) {
            return rule;
        }
        CategoryRenameIntent rename = intent.get();
        return new RuleDefinition(
                cleanName(rule == null ? null : rule.name()),
                Math.max(rule == null ? 0 : rule.confidence(), 0.95),
                new RuleConditions(List.of(), List.of(rename.sourceCategory()), "all", null, null, List.of()),
                new RuleActions(rename.targetCategory(), false, null, false, null)
        );
    }

    private Optional<CategoryRenameIntent> categoryRenameIntent(String prompt, Collection<String> allowedCategories) {
        Matcher matcher = CATEGORY_RENAME_PATTERN.matcher(prompt);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceCategory = normalizeCategory(matcher.group(1), allowedCategories);
        if (sourceCategory == null || !allowedCategories.contains(sourceCategory)) {
            return Optional.empty();
        }
        String targetCategory = cleanCategoryName(matcher.group(2));
        if (targetCategory == null) {
            return Optional.empty();
        }
        return Optional.of(new CategoryRenameIntent(sourceCategory, normalizeCategory(targetCategory, allowedCategories)));
    }

    private String cleanCategoryName(String value) {
        String cleaned = cleanString(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + cleaned.substring(1);
    }

    private record CategoryRenameIntent(String sourceCategory, String targetCategory) {
    }
}
