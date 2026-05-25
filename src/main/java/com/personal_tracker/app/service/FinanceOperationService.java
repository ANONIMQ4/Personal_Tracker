package com.personal_tracker.app.service;

import com.personal_tracker.app.model.FinanceOperation;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import com.personal_tracker.app.repository.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FinanceOperationService {

    private final FinanceOperationRepository financeOperationRepository;
    private final UserRepository userRepository;

    public FinanceOperationService(FinanceOperationRepository financeOperationRepository, UserRepository userRepository) {
        this.financeOperationRepository = financeOperationRepository;
        this.userRepository = userRepository;
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
        List<FinanceOperation> operations = new ArrayList<>();
        Set<String> operationKeys = new HashSet<>();
        int skippedCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeader(sheet.getRow(0));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmpty(row)) {
                    continue;
                }

                FinanceOperation operation = new FinanceOperation();
                operation.setUser(user);
                operation.setOperationDate(readDateTime(row, columns, "Дата операции"));
                operation.setPaymentDate(readDate(row, columns, "Дата платежа"));
                operation.setCardNumber(readText(row, columns, "Номер карты"));
                operation.setStatus(readText(row, columns, "Статус"));
                if (!isSuccessfulOperation(operation)) {
                    skippedCount++;
                    continue;
                }
                operation.setOperationAmount(readDecimal(row, columns, "Сумма операции"));
                operation.setOperationCurrency(readText(row, columns, "Валюта операции"));
                operation.setPaymentAmount(readDecimal(row, columns, "Сумма платежа"));
                operation.setPaymentCurrency(readText(row, columns, "Валюта платежа"));
                operation.setCashback(readDecimal(row, columns, "Кэшбэк"));
                operation.setCategory(readText(row, columns, "Категория"));
                operation.setMcc(readInteger(row, columns, "MCC"));
                operation.setDescription(readText(row, columns, "Описание"));
                normalizeCategory(operation);
                operation.setBonuses(readDecimal(row, columns, "Бонусы (включая кэшбэк)"));
                operation.setInvestmentRounding(readDecimal(row, columns, "Округление на инвесткопилку"));
                operation.setRoundedOperationAmount(readDecimal(row, columns, "Сумма операции с округлением"));
                operation.setSource("file");
                operation.setOperationKey(buildOperationKey(user.getId(), operation));

                if (operationKeys.contains(operation.getOperationKey()) || financeOperationRepository.existsSimilarOperation(
                        user.getId(),
                        operation.getOperationDate(),
                        operation.getOperationAmount(),
                        operation.getOperationCurrency(),
                        operation.getCategory(),
                        operation.getDescription()
                )) {
                    skippedCount++;
                    continue;
                }

                operationKeys.add(operation.getOperationKey());
                operations.add(operation);
            }
        }

        List<FinanceOperation> savedOperations = financeOperationRepository.saveAll(operations);
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
        operation.setOperationKey(buildOperationKey(user.getId(), operation));

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
        operation.setOperationKey(buildOperationKey(user.getId(), operation));

        FinanceOperation savedOperation = financeOperationRepository.save(operation);
        BigDecimal nextAmount = savedOperation.getOperationAmount() == null ? BigDecimal.ZERO : savedOperation.getOperationAmount();
        adjustBalance(user, nextAmount.subtract(previousAmount));
        return savedOperation;
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

    private void normalizeCategory(FinanceOperation operation) {
        if (operation.getOperationAmount() == null || operation.getDescription() == null) {
            return;
        }

        boolean isExpense = operation.getOperationAmount().signum() < 0;
        boolean isOzonBank = "Озон Банк (Ozon)".equalsIgnoreCase(operation.getDescription().trim());
        if (isExpense && isOzonBank) {
            operation.setCategory("Маркетплейсы");
        }
    }

    private boolean isSuccessfulOperation(FinanceOperation operation) {
        String status = operation.getStatus();
        return status == null || status.isBlank() || "OK".equalsIgnoreCase(status.trim());
    }

    private Map<String, Integer> readHeader(Row row) {
        if (row == null) {
            throw new IllegalArgumentException("В файле не найдена строка заголовков");
        }

        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : row) {
            String value = cell.getStringCellValue();
            if (value != null && !value.isBlank()) {
                columns.put(value.trim(), cell.getColumnIndex());
            }
        }
        return columns;
    }

    private boolean isEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && !readCellAsText(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readText(Row row, Map<String, Integer> columns, String columnName) {
        Cell cell = getCell(row, columns, columnName);
        if (cell == null) {
            return null;
        }

        String value = readCellAsText(cell);
        return value.isBlank() ? null : value;
    }

    private BigDecimal readDecimal(Row row, Map<String, Integer> columns, String columnName) {
        Cell cell = getCell(row, columns, columnName);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                String value = cell.getStringCellValue().replace(" ", "").replace(",", ".").trim();
                yield value.isBlank() ? null : new BigDecimal(value);
            }
            default -> null;
        };
    }

    private Integer readInteger(Row row, Map<String, Integer> columns, String columnName) {
        BigDecimal value = readDecimal(row, columns, columnName);
        return value == null ? null : value.intValue();
    }

    private LocalDate readDate(Row row, Map<String, Integer> columns, String columnName) {
        LocalDateTime value = readDateTime(row, columns, columnName);
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime readDateTime(Row row, Map<String, Integer> columns, String columnName) {
        Cell cell = getCell(row, columns, columnName);
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return null;
    }

    private Cell getCell(Row row, Map<String, Integer> columns, String columnName) {
        Integer index = columns.get(columnName);
        return index == null ? null : row.getCell(index);
    }

    private String readCellAsText(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String defaultCategory(String type) {
        return "income".equals(type) ? "Прочий доход" : "Прочий расход";
    }

    private String buildOperationKey(Long userId, FinanceOperation operation) {
        String rawKey = String.join("|",
                value(userId),
                value(operation.getOperationDate()),
                value(operation.getOperationAmount()),
                value(operation.getOperationCurrency()),
                value(operation.getCategory()),
                value(operation.getDescription()),
                value(operation.getSource())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase();
    }

    public record ImportResult(int importedCount, int skippedCount) {
    }
}
