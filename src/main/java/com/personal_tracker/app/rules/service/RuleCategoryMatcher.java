package com.personal_tracker.app.rules.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

final class RuleCategoryMatcher {

    private RuleCategoryMatcher() {
    }

    static String canonicalize(String value, Collection<String> allowedCategories) {
        String cleaned = clean(value);
        if (cleaned == null || allowedCategories == null || allowedCategories.isEmpty()) {
            return cleaned;
        }

        String valueKey = key(cleaned);
        String exactMatch = allowedCategories.stream()
                .filter(Objects::nonNull)
                .map(RuleCategoryMatcher::clean)
                .filter(Objects::nonNull)
                .filter(category -> key(category).equals(valueKey))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch;
        }

        java.util.List<CategoryDistance> candidates = allowedCategories.stream()
                .filter(Objects::nonNull)
                .map(RuleCategoryMatcher::clean)
                .filter(Objects::nonNull)
                .map(category -> new CategoryDistance(category, distance(valueKey, key(category))))
                .filter(candidate -> candidate.distance() <= typoThreshold(valueKey))
                .sorted(Comparator.comparingInt(CategoryDistance::distance))
                .limit(2)
                .toList();
        if (candidates.isEmpty()) {
            return cleaned;
        }
        if (candidates.size() > 1 && candidates.get(0).distance() == candidates.get(1).distance()) {
            return cleaned;
        }
        return candidates.get(0).category();
    }

    static boolean same(String left, String right) {
        String leftKey = key(clean(left));
        String rightKey = key(clean(right));
        return !leftKey.isBlank() && leftKey.equals(rightKey);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String key(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "")
                .trim();
    }

    private static int typoThreshold(String valueKey) {
        if (valueKey.length() < 6) {
            return 0;
        }
        if (valueKey.length() < 11) {
            return 1;
        }
        return 2;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record CategoryDistance(String category, int distance) {
    }
}
