package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.repository.FinanceOperationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FinanceAnalyticsService {

    private static final String DEFAULT_CATEGORY = "Без категории";
    private static final String OTHER_CATEGORY = "Остальное";

    private final FinanceOperationRepository financeOperationRepository;

    public FinanceAnalyticsService(FinanceOperationRepository financeOperationRepository) {
        this.financeOperationRepository = financeOperationRepository;
    }

    public AnalyticsResponse buildAnalytics(
            Long userId,
            LocalDate from,
            LocalDate to,
            String metric,
            String donutMode,
            List<String> selectedCategoryKeys
    ) {
        List<FinanceOperation> operations = financeOperationRepository.findVisibleByUserIdOrderByOperationDateDesc(userId);
        List<FinanceOperation> analyticsOperations = operations.stream()
                .filter(operation -> !operation.isExcludeFromAnalytics())
                .toList();
        List<FinanceOperation> periodAnalyticsOperations = filterByPeriod(analyticsOperations, from, to);
        List<ChartCategoryGroup> categoryGroups = chartCategoryGroups(analyticsOperations);
        Set<String> selectedKeys = selectedKeys(selectedCategoryKeys, categoryGroups);

        return new AnalyticsResponse(
                summary(periodAnalyticsOperations),
                periods(operations),
                donutCategories(periodAnalyticsOperations, "income".equals(donutMode) ? "income" : "expense"),
                categoryGroups,
                chart(periodAnalyticsOperations, selectedKeys, metric, from, to)
        );
    }

    private List<FinanceOperation> filterByPeriod(List<FinanceOperation> operations, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return operations;
        }

        LocalDateTime fromDateTime = from == null ? LocalDateTime.MIN : from.atStartOfDay();
        LocalDateTime toDateTime = to == null ? LocalDateTime.MAX : to.plusDays(1).atStartOfDay().minusNanos(1);
        return operations.stream()
                .filter(operation -> {
                    LocalDateTime date = operation.getOperationDate();
                    return date != null && !date.isBefore(fromDateTime) && !date.isAfter(toDateTime);
                })
                .toList();
    }

    private Summary summary(List<FinanceOperation> operations) {
        double incomeTotal = 0;
        double expenseTotal = 0;
        Map<String, Double> expenseByCategory = new LinkedHashMap<>();

        for (FinanceOperation operation : operations) {
            double amount = amount(operation);
            if (amount >= 0) {
                incomeTotal += amount;
            } else {
                double expense = Math.abs(amount);
                expenseTotal += expense;
                expenseByCategory.merge(category(operation), expense, Double::sum);
            }
        }

        CategoryAmount topCategory = expenseByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue()))
                .orElse(null);

        return new Summary(incomeTotal, expenseTotal, incomeTotal - expenseTotal, topCategory);
    }

    private Periods periods(List<FinanceOperation> operations) {
        Map<Integer, Set<String>> monthKeysByYear = new LinkedHashMap<>();
        operations.stream()
                .map(FinanceOperation::getOperationDate)
                .filter(date -> date != null)
                .sorted(Comparator.reverseOrder())
                .forEach(date -> {
                    int year = date.getYear();
                    monthKeysByYear.computeIfAbsent(year, ignored -> new LinkedHashSet<>())
                            .add(YearMonth.from(date).toString());
                });

        List<PeriodYear> years = monthKeysByYear.entrySet().stream()
                .map(entry -> new PeriodYear(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(monthKey -> new PeriodMonth(monthKey, monthLabel(monthKey)))
                                .toList()
                ))
                .toList();

        String latestMonthKey = years.isEmpty() || years.get(0).months().isEmpty()
                ? null
                : years.get(0).months().get(0).key();
        return new Periods(years, latestMonthKey);
    }

    private List<CategoryAmount> donutCategories(List<FinanceOperation> operations, String mode) {
        Map<String, Double> totals = new LinkedHashMap<>();
        boolean incomeMode = "income".equals(mode);

        for (FinanceOperation operation : operations) {
            double amount = amount(operation);
            if (incomeMode && amount <= 0 || !incomeMode && amount >= 0) {
                continue;
            }

            totals.merge(category(operation), Math.abs(amount), Double::sum);
        }

        List<CategoryAmount> categories = totals.entrySet().stream()
                .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryAmount::amount).reversed())
                .toList();

        if (categories.size() <= 6) {
            return categories;
        }

        List<CategoryAmount> visible = new ArrayList<>(categories.subList(0, 5));
        double otherAmount = categories.subList(5, categories.size()).stream()
                .mapToDouble(CategoryAmount::amount)
                .sum();
        visible.add(new CategoryAmount(OTHER_CATEGORY, otherAmount));
        return visible;
    }

    private List<ChartCategoryGroup> chartCategoryGroups(List<FinanceOperation> operations) {
        Map<String, ChartCategory> income = new LinkedHashMap<>();
        Map<String, ChartCategory> expense = new LinkedHashMap<>();

        for (FinanceOperation operation : operations) {
            String type = amount(operation) >= 0 ? "income" : "expense";
            String normalizedCategory = category(operation);
            ChartCategory chartCategory = new ChartCategory(
                    chartCategoryKey(type, normalizedCategory),
                    type,
                    normalizedCategory,
                    typedCategoryLabel(type, normalizedCategory)
            );
            if ("income".equals(type)) {
                income.put(normalizedCategory, chartCategory);
            } else {
                expense.put(normalizedCategory, chartCategory);
            }
        }

        List<ChartCategory> incomeItems = income.values().stream()
                .sorted(Comparator.comparing(ChartCategory::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ChartCategory> expenseItems = expense.values().stream()
                .sorted(Comparator.comparing(ChartCategory::label, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return List.of(
                new ChartCategoryGroup("income", "Доходы", incomeItems),
                new ChartCategoryGroup("expense", "Расходы", expenseItems)
        );
    }

    private Set<String> selectedKeys(List<String> requestedKeys, List<ChartCategoryGroup> groups) {
        Set<String> availableKeys = new LinkedHashSet<>();
        groups.forEach(group -> group.items().forEach(item -> availableKeys.add(item.key())));
        if (requestedKeys == null || requestedKeys.isEmpty()) {
            return availableKeys;
        }

        Set<String> selectedKeys = new LinkedHashSet<>(requestedKeys);
        selectedKeys.retainAll(availableKeys);
        return selectedKeys;
    }

    private ChartData chart(
            List<FinanceOperation> operations,
            Set<String> selectedKeys,
            String metric,
            LocalDate selectedFrom,
            LocalDate selectedTo
    ) {
        String normalizedMetric = "count".equals(metric) ? "count" : "amount";
        List<FinanceOperation> chartOperations = operations.stream()
                .filter(operation -> operation.getOperationDate() != null)
                .filter(operation -> selectedKeys.contains(chartCategoryKey(operation)))
                .toList();

        DateRange range = chartRange(chartOperations, selectedFrom, selectedTo);
        if (range == null) {
            return new ChartData(normalizedMetric, null, null, false, List.of(), 0, 0, 0);
        }

        boolean useMonths = range.from().plusDays(95).isBefore(range.to());
        List<ChartBucket> buckets = chartBuckets(range.from(), range.to(), useMonths);
        Map<String, ChartBucketBuilder> bucketsByKey = new LinkedHashMap<>();
        buckets.forEach(bucket -> bucketsByKey.put(bucket.key(), new ChartBucketBuilder(bucket.key(), bucket.label())));

        for (FinanceOperation operation : chartOperations) {
            LocalDate operationDate = operation.getOperationDate().toLocalDate();
            String key = useMonths ? YearMonth.from(operationDate).toString() : operationDate.toString();
            ChartBucketBuilder bucket = bucketsByKey.get(key);
            if (bucket != null) {
                bucket.value += "count".equals(normalizedMetric) ? 1 : Math.abs(amount(operation));
            }
        }

        List<ChartBucket> resultBuckets = bucketsByKey.values().stream()
                .map(ChartBucketBuilder::build)
                .toList();
        double total = resultBuckets.stream().mapToDouble(ChartBucket::value).sum();
        double max = resultBuckets.stream().mapToDouble(ChartBucket::value).max().orElse(0);
        double average = resultBuckets.isEmpty() ? 0 : total / resultBuckets.size();
        return new ChartData(normalizedMetric, range.from(), range.to(), useMonths, resultBuckets, total, average, max);
    }

    private DateRange chartRange(List<FinanceOperation> operations, LocalDate selectedFrom, LocalDate selectedTo) {
        if (selectedFrom != null && selectedTo != null) {
            return selectedFrom.isAfter(selectedTo) ? null : new DateRange(selectedFrom, selectedTo);
        }
        if (operations.isEmpty()) {
            return null;
        }

        LocalDate firstOperationDate = operations.stream()
                .map(operation -> operation.getOperationDate().toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate lastOperationDate = operations.stream()
                .map(operation -> operation.getOperationDate().toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow();

        LocalDate from = selectedFrom == null ? firstOperationDate : selectedFrom;
        LocalDate to = selectedTo == null ? lastOperationDate : selectedTo;
        return from.isAfter(to) ? null : new DateRange(from, to);
    }

    private List<ChartBucket> chartBuckets(LocalDate from, LocalDate to, boolean useMonths) {
        List<ChartBucket> buckets = new ArrayList<>();
        if (useMonths) {
            YearMonth cursor = YearMonth.from(from);
            YearMonth end = YearMonth.from(to);
            while (!cursor.isAfter(end)) {
                buckets.add(new ChartBucket(cursor.toString(), shortMonthLabel(cursor) + " " + String.valueOf(cursor.getYear()).substring(2), 0));
                cursor = cursor.plusMonths(1);
            }
            return buckets;
        }

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            buckets.add(new ChartBucket(cursor.toString(), String.format("%02d.%02d", cursor.getDayOfMonth(), cursor.getMonthValue()), 0));
            cursor = cursor.plusDays(1);
        }
        return buckets;
    }

    private String chartCategoryKey(FinanceOperation operation) {
        String type = amount(operation) >= 0 ? "income" : "expense";
        return chartCategoryKey(type, category(operation));
    }

    private String chartCategoryKey(String type, String category) {
        return type + "::" + category;
    }

    private String typedCategoryLabel(String type, String category) {
        if ("Переводы".equals(category)) {
            return "income".equals(type) ? "Входящие переводы" : "Исходящие переводы";
        }
        return category;
    }

    private String category(FinanceOperation operation) {
        String category = operation.getCategory();
        return category == null || category.isBlank() ? DEFAULT_CATEGORY : category;
    }

    private double amount(FinanceOperation operation) {
        BigDecimal amount = operation.getOperationAmount();
        return amount == null ? 0 : amount.doubleValue();
    }

    private String monthLabel(String monthKey) {
        YearMonth yearMonth = YearMonth.parse(monthKey);
        String month = yearMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru"));
        return month + " " + yearMonth.getYear();
    }

    private String shortMonthLabel(YearMonth yearMonth) {
        String month = yearMonth.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, Locale.forLanguageTag("ru"))
                .replace(".", "");
        return month.substring(0, 1).toUpperCase(Locale.forLanguageTag("ru")) + month.substring(1);
    }

    private static class ChartBucketBuilder {
        private final String key;
        private final String label;
        private double value;

        private ChartBucketBuilder(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private ChartBucket build() {
            return new ChartBucket(key, label, value);
        }
    }

    public record AnalyticsResponse(
            Summary summary,
            Periods periods,
            List<CategoryAmount> donutCategories,
            List<ChartCategoryGroup> categoryGroups,
            ChartData chart
    ) {
    }

    public record Summary(double incomeTotal, double expenseTotal, double balance, CategoryAmount topCategory) {
    }

    public record CategoryAmount(String name, double amount) {
    }

    public record Periods(List<PeriodYear> years, String latestMonthKey) {
    }

    public record PeriodYear(int year, List<PeriodMonth> months) {
    }

    public record PeriodMonth(String key, String label) {
    }

    public record ChartCategoryGroup(String type, String title, List<ChartCategory> items) {
    }

    public record ChartCategory(String key, String type, String category, String label) {
    }

    public record ChartData(
            String metric,
            LocalDate from,
            LocalDate to,
            boolean useMonths,
            List<ChartBucket> buckets,
            double total,
            double average,
            double max
    ) {
    }

    public record ChartBucket(String key, String label, double value) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
