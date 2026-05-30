package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.entity.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FinanceOperationService {

    private final FinanceOperationRepository financeOperationRepository;
    private final UserRepository userRepository;
    private final OperationExcelParser operationExcelParser;
    private final OperationKeyBuilder operationKeyBuilder;

    public FinanceOperationService(
            FinanceOperationRepository financeOperationRepository,
            UserRepository userRepository,
            OperationExcelParser operationExcelParser,
            OperationKeyBuilder operationKeyBuilder
    ) {
        this.financeOperationRepository = financeOperationRepository;
        this.userRepository = userRepository;
        this.operationExcelParser = operationExcelParser;
        this.operationKeyBuilder = operationKeyBuilder;
    }

    public List<FinanceOperation> getOperations(Long userId) {
        return financeOperationRepository.findVisibleByUserIdOrderByOperationDateDesc(userId);
    }

    @Transactional
    public long deleteOperations(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<FinanceOperation> operations = financeOperationRepository.findByUserIdAndIdIn(userId, ids);
        long deletedCount = financeOperationRepository.deleteByUserIdAndIdIn(userId, ids);
        adjustBalance(userId, sumAmounts(operations).negate());
        return deletedCount;
    }

    @Transactional
    public long deleteOperationsByPeriod(Long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Некорректный период");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay().minusNanos(1);
        List<FinanceOperation> operations = financeOperationRepository.findByUserIdAndOperationDateBetween(userId, fromDateTime, toDateTime);
        long deletedCount = financeOperationRepository.deleteByUserIdAndOperationDateBetween(userId, fromDateTime, toDateTime);
        adjustBalance(userId, sumAmounts(operations).negate());
        return deletedCount;
    }

    @Transactional
    public ImportResult importOperations(User user, MultipartFile file) throws IOException {
        OperationExcelParser.ParseResult parseResult = operationExcelParser.parse(user, file);
        List<FinanceOperation> operations = parseResult.operations();
        List<FinanceOperation> acceptedOperations = new ArrayList<>();
        Set<String> operationKeys = new HashSet<>();
        int skippedCount = parseResult.skippedCount();

        for (FinanceOperation operation : operations) {
            refreshOperationKey(user.getId(), operation);
            if (isDuplicateImport(user.getId(), operation, operationKeys)) {
                skippedCount++;
                continue;
            }

            acceptedOperations.add(operation);
        }

        List<FinanceOperation> savedOperations = financeOperationRepository.saveAll(acceptedOperations);
        adjustBalance(user, sumAmounts(savedOperations));
        return new ImportResult(savedOperations.size(), skippedCount);
    }

    @Transactional
    public FinanceOperation createManualOperation(
            User user,
            String type,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDateTime operationDate
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        BigDecimal normalizedAmount = amount.abs();
        if ("expense".equals(type)) {
            normalizedAmount = normalizedAmount.negate();
        }

        FinanceOperation operation = new FinanceOperation();
        operation.setUser(user);
        operation.setOperationDate(operationDate == null ? LocalDateTime.now() : operationDate);
        operation.setOperationAmount(normalizedAmount);
        operation.setOperationCurrency(currency == null || currency.isBlank() ? "RUB" : currency.trim().toUpperCase());
        operation.setCategory(category == null || category.isBlank() ? defaultCategory(type) : category.trim());
        operation.setDescription(description == null || description.isBlank() ? null : description.trim());
        operation.setSource("manual");
        refreshOperationKey(user.getId(), operation);

        if (financeOperationRepository.existsByUserIdAndOperationKey(user.getId(), operation.getOperationKey())) {
            throw new IllegalArgumentException("Такая операция уже добавлена");
        }

        FinanceOperation savedOperation = financeOperationRepository.save(operation);
        adjustBalance(user, savedOperation.getOperationAmount());
        return savedOperation;
    }

    @Transactional
    public FinanceOperation updateOperation(
            User user,
            Long operationId,
            BigDecimal operationAmount,
            String category,
            String description
    ) {
        FinanceOperation operation = financeOperationRepository.findById(operationId)
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));

        BigDecimal previousAmount = operation.getOperationAmount() == null ? BigDecimal.ZERO : operation.getOperationAmount();
        if (operationAmount != null) {
            operation.setOperationAmount(operationAmount);
        }
        if (category != null) {
            operation.setCategory(category.isBlank() ? null : category.trim());
        }
        if (description != null) {
            operation.setDescription(description.isBlank() ? null : description.trim());
        }
        refreshOperationKey(user.getId(), operation);

        FinanceOperation savedOperation = financeOperationRepository.save(operation);
        BigDecimal nextAmount = savedOperation.getOperationAmount() == null ? BigDecimal.ZERO : savedOperation.getOperationAmount();
        adjustBalance(user, nextAmount.subtract(previousAmount));
        return savedOperation;
    }

    public void refreshOperationKey(Long userId, FinanceOperation operation) {
        operation.setOperationKey(operationKeyBuilder.build(userId, operation));
    }

    private boolean isDuplicateImport(Long userId, FinanceOperation operation, Set<String> currentFileKeys) {
        return !currentFileKeys.add(operation.getOperationKey())
                || financeOperationRepository.existsSimilarOperation(
                        userId,
                        operation.getOperationDate(),
                        operation.getOperationAmount(),
                        operation.getOperationCurrency(),
                        operation.getCategory(),
                        operation.getDescription()
                );
    }

    private void adjustBalance(User user, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        user.setAccountBalance(user.getAccountBalance().add(delta));
        userRepository.save(user);
    }

    private void adjustBalance(Long userId, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> adjustBalance(user, delta));
    }

    private BigDecimal sumAmounts(List<FinanceOperation> operations) {
        return operations.stream()
                .map(FinanceOperation::getOperationAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String defaultCategory(String type) {
        return "income".equals(type) ? "Прочий доход" : "Прочий расход";
    }

    public record ImportResult(int importedCount, int skippedCount) {
    }
}
