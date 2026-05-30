package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.entity.User;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OperationExcelParser {

    public ParseResult parse(User user, MultipartFile file) throws IOException {
        List<FinanceOperation> operations = new ArrayList<>();
        int skippedCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeader(sheet.getRow(0));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmpty(row)) {
                    continue;
                }

                FinanceOperation operation = readOperation(user, row, columns);
                if (!isSuccessfulOperation(operation)) {
                    skippedCount++;
                    continue;
                }

                normalizeCategory(operation);
                operations.add(operation);
            }
        }

        return new ParseResult(operations, skippedCount);
    }

    private FinanceOperation readOperation(User user, Row row, Map<String, Integer> columns) {
        FinanceOperation operation = new FinanceOperation();
        operation.setUser(user);
        operation.setOperationDate(readDateTime(row, columns, "Дата операции"));
        operation.setPaymentDate(readDate(row, columns, "Дата платежа"));
        operation.setCardNumber(readText(row, columns, "Номер карты"));
        operation.setStatus(readText(row, columns, "Статус"));
        operation.setOperationAmount(readDecimal(row, columns, "Сумма операции"));
        operation.setOperationCurrency(readText(row, columns, "Валюта операции"));
        operation.setPaymentAmount(readDecimal(row, columns, "Сумма платежа"));
        operation.setPaymentCurrency(readText(row, columns, "Валюта платежа"));
        operation.setCashback(readDecimal(row, columns, "Кэшбэк"));
        operation.setCategory(readText(row, columns, "Категория"));
        operation.setMcc(readInteger(row, columns, "MCC"));
        operation.setDescription(readText(row, columns, "Описание"));
        operation.setBonuses(readDecimal(row, columns, "Бонусы (включая кэшбэк)"));
        operation.setInvestmentRounding(readDecimal(row, columns, "Округление на инвесткопилку"));
        operation.setRoundedOperationAmount(readDecimal(row, columns, "Сумма операции с округлением"));
        operation.setSource("file");
        return operation;
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

    public record ParseResult(List<FinanceOperation> operations, int skippedCount) {
    }
}
