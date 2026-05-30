package com.personal_tracker.app.service;

import com.personal_tracker.app.repository.FinanceOperationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FinanceCategoryService {

    private static final String DEFAULT_COLOR = "#b8c0cc";
    private static final double GOLDEN_ANGLE = 137.508;
    private static final double PASTEL_SATURATION = 0.52;
    private static final double PASTEL_LIGHTNESS = 0.76;

    private final FinanceOperationRepository financeOperationRepository;

    public FinanceCategoryService(FinanceOperationRepository financeOperationRepository) {
        this.financeOperationRepository = financeOperationRepository;
    }

    public List<CategoryDto> getUserCategories(Long userId) {
        List<String> categories = financeOperationRepository.findVisibleCategoriesByUserId(userId);
        return java.util.stream.IntStream.range(0, categories.size())
                .mapToObj(index -> toDto(categories.get(index), index))
                .toList();
    }

    private CategoryDto toDto(String category, int index) {
        String name = category == null || category.isBlank() ? "Без категории" : category.trim();
        if ("Без категории".equals(name)) {
            return new CategoryDto(name, DEFAULT_COLOR);
        }
        return new CategoryDto(name, pastelColor(index));
    }

    private String pastelColor(int index) {
        double hue = index * GOLDEN_ANGLE % 360;
        return hslToHex(hue, PASTEL_SATURATION, PASTEL_LIGHTNESS);
    }

    private String hslToHex(double hue, double saturation, double lightness) {
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double huePart = hue / 60;
        double x = chroma * (1 - Math.abs(huePart % 2 - 1));
        double match = lightness - chroma / 2;
        double[] rgb = hueToRgb(huePart, chroma, x);

        return "#%02x%02x%02x".formatted(
                toRgb(rgb[0], match),
                toRgb(rgb[1], match),
                toRgb(rgb[2], match)
        );
    }

    private double[] hueToRgb(double huePart, double chroma, double x) {
        return switch ((int) Math.floor(huePart)) {
            case 0 -> new double[]{chroma, x, 0};
            case 1 -> new double[]{x, chroma, 0};
            case 2 -> new double[]{0, chroma, x};
            case 3 -> new double[]{0, x, chroma};
            case 4 -> new double[]{x, 0, chroma};
            default -> new double[]{chroma, 0, x};
        };
    }

    private int toRgb(double color, double match) {
        return (int) Math.round((color + match) * 255);
    }

    public record CategoryDto(String name, String color) {
    }
}
