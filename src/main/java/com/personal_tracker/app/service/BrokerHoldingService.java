package com.personal_tracker.app.service;

import com.personal_tracker.app.model.BrokerHolding;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.BrokerHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class BrokerHoldingService {

    private final BrokerHoldingRepository brokerHoldingRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BrokerHoldingService(BrokerHoldingRepository brokerHoldingRepository, ObjectMapper objectMapper) {
        this.brokerHoldingRepository = brokerHoldingRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://iss.moex.com")
                .build();
    }

    public List<BrokerHolding> getHoldings(Long userId) {
        return brokerHoldingRepository.findByUserIdOrderByHoldingTypeAscNameAsc(userId);
    }

    @Transactional
    public BrokerHolding addHolding(User user, HoldingRequest request) {
        String holdingType = clean(request.holdingType());
        String code = clean(request.ticker());
        String quantityText = clean(request.quantityText());
        if (holdingType == null || code == null || quantityText == null) {
            throw new IllegalArgumentException("Тип, код и количество обязательны");
        }

        MoexSecurity security = loadSecurity(holdingType, code);

        BrokerHolding holding = new BrokerHolding();
        holding.setUser(user);
        holding.setHoldingType(security.holdingType());
        holding.setTicker(security.ticker());
        holding.setName(security.name());
        holding.setPriceText(formatRub(security.price()));
        holding.setQuantityText(formatQuantity(quantityText, security.holdingType()));
        holding.setValueText(formatRub(security.price().multiply(parseQuantity(quantityText))));
        return brokerHoldingRepository.save(holding);
    }

    @Transactional
    public BrokerHolding updateQuantity(Long userId, Long holdingId, QuantityRequest request) {
        String quantityText = clean(request.quantityText());
        if (quantityText == null) {
            throw new IllegalArgumentException("Количество обязательно");
        }

        BrokerHolding holding = brokerHoldingRepository.findByIdAndUserId(holdingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Позиция не найдена"));
        BigDecimal quantity = parseQuantity(quantityText);
        holding.setQuantityText(formatQuantity(quantityText, holding.getHoldingType()));

        BigDecimal price = parseMoney(holding.getPriceText());
        if (price != null) {
            holding.setValueText(formatRub(price.multiply(quantity)));
        }
        return brokerHoldingRepository.save(holding);
    }

    @Transactional
    public void deleteHolding(Long userId, Long holdingId) {
        BrokerHolding holding = brokerHoldingRepository.findByIdAndUserId(holdingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Позиция не найдена"));
        brokerHoldingRepository.delete(holding);
    }

    private MoexSecurity loadSecurity(String holdingType, String code) {
        String normalizedType = normalizeType(holdingType);
        String ticker = normalizeTicker(code, normalizedType);
        return switch (normalizedType) {
            case "Акции" -> loadStockSecurity(ticker);
            case "Облигации" -> loadBondSecurity(ticker);
            case "Валюта" -> loadCurrencySecurity(ticker);
            default -> throw new IllegalArgumentException("Неизвестный тип позиции");
        };
    }

    private MoexSecurity loadStockSecurity(String ticker) {
        JsonNode root = getMoexJson("/iss/engines/stock/markets/shares/boards/TQBR/securities/" + ticker
                + ".json?iss.meta=off&iss.only=securities,marketdata");
        String name = textFromTable(root, "securities", 0, "SHORTNAME", "SECNAME");
        BigDecimal price = firstPrice(root, "marketdata", "LAST", "LCURRENTPRICE", "MARKETPRICE", "MARKETPRICE2")
                .or(() -> firstPrice(root, "securities", "PREVPRICE"))
                .orElseThrow(() -> new IllegalArgumentException("Не удалось получить цену с Мосбиржи"));
        return new MoexSecurity("Акции", ticker, name == null ? ticker : name, price);
    }

    private MoexSecurity loadBondSecurity(String ticker) {
        JsonNode root = getMoexJson("/iss/engines/stock/markets/bonds/boards/TQCB/securities/" + ticker
                + ".json?iss.meta=off&iss.only=securities,marketdata");
        String name = textFromTable(root, "securities", 0, "SECNAME", "SHORTNAME");
        BigDecimal percentPrice = firstPrice(root, "marketdata", "LAST", "LCURRENTPRICE", "MARKETPRICE", "MARKETPRICE2")
                .or(() -> firstPrice(root, "securities", "PREVPRICE"))
                .orElseThrow(() -> new IllegalArgumentException("Не удалось получить цену с Мосбиржи"));
        BigDecimal faceValue = firstPrice(root, "securities", "FACEVALUE").orElse(BigDecimal.valueOf(1000));
        BigDecimal rubPrice = percentPrice.multiply(faceValue).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return new MoexSecurity("Облигации", ticker, name == null ? ticker : name, rubPrice);
    }

    private MoexSecurity loadCurrencySecurity(String ticker) {
        JsonNode root = getMoexJson("/iss/engines/currency/markets/selt/boards/CETS/securities/" + ticker
                + ".json?iss.meta=off&iss.only=securities,marketdata");
        String name = textFromTable(root, "securities", 0, "SHORTNAME", "SECNAME");
        BigDecimal price = firstPrice(root, "marketdata", "LAST", "LCURRENTPRICE", "MARKETPRICE", "MARKETPRICE2")
                .or(() -> firstPrice(root, "securities", "PREVPRICE"))
                .orElseThrow(() -> new IllegalArgumentException("Не удалось получить курс с Мосбиржи"));
        return new MoexSecurity("Валюта", ticker, name == null ? ticker : name, price);
    }

    private JsonNode getMoexJson(String path) {
        try {
            String body = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Не удалось получить данные с Мосбиржи");
        }
    }

    private java.util.Optional<BigDecimal> firstPrice(JsonNode root, String tableName, String... columnNames) {
        JsonNode table = root.path(tableName);
        JsonNode data = table.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return java.util.Optional.empty();
        }

        for (JsonNode row : data) {
            for (String columnName : columnNames) {
                int columnIndex = columnIndex(table, columnName);
                if (columnIndex >= 0 && columnIndex < row.size() && !row.get(columnIndex).isNull()) {
                    BigDecimal value = decimal(row.get(columnIndex));
                    if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                        return java.util.Optional.of(value);
                    }
                }
            }
        }
        return java.util.Optional.empty();
    }

    private String textFromTable(JsonNode root, String tableName, int rowIndex, String... columnNames) {
        JsonNode table = root.path(tableName);
        JsonNode data = table.path("data");
        if (!data.isArray() || data.size() <= rowIndex) {
            return null;
        }

        JsonNode row = data.get(rowIndex);
        for (String columnName : columnNames) {
            int index = columnIndex(table, columnName);
            if (index >= 0 && index < row.size() && !row.get(index).isNull()) {
                return row.get(index).asText();
            }
        }
        return null;
    }

    private int columnIndex(JsonNode table, String columnName) {
        JsonNode columns = table.path("columns");
        for (int index = 0; index < columns.size(); index++) {
            if (columnName.equalsIgnoreCase(columns.get(index).asText())) {
                return index;
            }
        }
        return -1;
    }

    private BigDecimal decimal(JsonNode value) {
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText().replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeType(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("акц") || "stock".equals(normalized) || "share".equals(normalized)) {
            return "Акции";
        }
        if (normalized.contains("облиг") || "bond".equals(normalized)) {
            return "Облигации";
        }
        if (normalized.contains("валют") || "currency".equals(normalized)) {
            return "Валюта";
        }
        return value.trim();
    }

    private String normalizeTicker(String value, String holdingType) {
        String ticker = value.trim().toUpperCase(Locale.ROOT);
        if ("Валюта".equals(holdingType)) {
            return switch (ticker) {
                case "USD" -> "USD000UTSTOM";
                case "EUR" -> "EUR_RUB__TOM";
                case "CNY" -> "CNYRUB_TOM";
                default -> ticker;
            };
        }
        return ticker;
    }

    private BigDecimal parseQuantity(String value) {
        try {
            BigDecimal quantity = new BigDecimal(value.replace(",", ".").replaceAll("[^0-9.]", ""));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return quantity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Количество должно быть больше нуля");
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace("₽", "").replace(" ", "").replace(",", ".").trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatRub(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.toPlainString().replace(".", ",") + " ₽";
    }

    private String formatQuantity(String value, String holdingType) {
        String suffix = "Валюта".equals(holdingType) ? "" : " шт.";
        return parseQuantity(value).stripTrailingZeros().toPlainString().replace(".", ",") + suffix;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record HoldingRequest(String holdingType, String ticker, String quantityText) {
    }

    public record QuantityRequest(String quantityText) {
    }

    private record MoexSecurity(String holdingType, String ticker, String name, BigDecimal price) {
    }
}
