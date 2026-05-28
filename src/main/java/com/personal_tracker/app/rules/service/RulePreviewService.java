package com.personal_tracker.app.rules.service;

import com.personal_tracker.app.model.FinanceOperation;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.rules.dto.RulePreviewResponse;
import com.personal_tracker.app.rules.dto.RulePreviewResponse.RulePreviewChange;
import com.personal_tracker.app.rules.model.RuleDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RulePreviewService {

    private static final int PREVIEW_LIMIT = 50;

    private final FinanceOperationRepository financeOperationRepository;
    private final RuleEngine ruleEngine;

    public RulePreviewService(FinanceOperationRepository financeOperationRepository, RuleEngine ruleEngine) {
        this.financeOperationRepository = financeOperationRepository;
        this.ruleEngine = ruleEngine;
    }

    public RulePreviewResponse preview(Long userId, RuleDefinition rule) {
        List<FinanceOperation> affectedOperations = affectedOperations(userId, rule);
        List<RulePreviewChange> changes = affectedOperations.stream()
                .limit(PREVIEW_LIMIT)
                .map(operation -> new RulePreviewChange(
                        operation.getId(),
                        operation.getOperationDate(),
                        operation.getDescription(),
                        ruleEngine.stateOf(operation),
                        ruleEngine.previewAfter(operation, rule)
                ))
                .toList();
        return new RulePreviewResponse(affectedOperations.size(), changes);
    }

    public List<FinanceOperation> affectedOperations(Long userId, RuleDefinition rule) {
        return financeOperationRepository.findVisibleByUserIdOrderByOperationDateDesc(userId).stream()
                .filter(operation -> ruleEngine.matches(operation, rule))
                .toList();
    }
}
