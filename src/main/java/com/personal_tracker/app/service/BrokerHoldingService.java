package com.personal_tracker.app.service;

import com.personal_tracker.app.model.BrokerHolding;
import com.personal_tracker.app.model.User;
import com.personal_tracker.app.repository.BrokerHoldingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BrokerHoldingService {

    private static final Pattern TICKER_PATTERN = Pattern.compile("(?i)(?:RU[A-ZА-Я0-9]{10}|USD[A-ZА-Я0-9]{9}|RUB)");
    private static final Pattern MONEY_PATTERN = Pattern.compile("[+-]?\\d[\\d\\s]*,\\d{1,2}\\s*[₽РP]");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("[+-]?\\d+[,.]\\d+\\s*%");

    private final BrokerHoldingRepository brokerHoldingRepository;
    private final String tesseractCommand;
    private final String tesseractLanguages;

    public BrokerHoldingService(
            BrokerHoldingRepository brokerHoldingRepository,
            @Value("${broker.ocr.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${broker.ocr.languages:rus+eng}") String tesseractLanguages
    ) {
        this.brokerHoldingRepository = brokerHoldingRepository;
        this.tesseractCommand = tesseractCommand;
        this.tesseractLanguages = tesseractLanguages;
    }

    public List<BrokerHolding> getHoldings(Long userId) {
        return brokerHoldingRepository.findByUserIdOrderByHoldingTypeAscNameAsc(userId);
    }

    @Transactional
    public ImportResult importScreenshot(User user, MultipartFile screenshot) throws IOException, InterruptedException {
        if (screenshot == null || screenshot.isEmpty()) {
            throw new IllegalArgumentException("Скриншот не выбран");
        }

        String text = recognizeText(screenshot);
        List<BrokerHolding> holdings = parseHoldings(user, text);
        if (holdings.isEmpty()) {
            throw new IllegalArgumentException("Не удалось распознать позиции на скриншоте");
        }

        brokerHoldingRepository.deleteByUserId(user.getId());
        List<BrokerHolding> savedHoldings = brokerHoldingRepository.saveAll(holdings);
        return new ImportResult(savedHoldings.size(), text);
    }

    private String recognizeText(MultipartFile screenshot) throws IOException, InterruptedException {
        Path imagePath = Files.createTempFile("broker-screenshot-", extension(screenshot.getOriginalFilename()));
        Path outputBasePath = Files.createTempFile("broker-ocr-", "");
        Files.deleteIfExists(outputBasePath);

        try {
            screenshot.transferTo(imagePath);
            Process process = new ProcessBuilder(
                    tesseractCommand,
                    imagePath.toString(),
                    "stdout",
                    "-l",
                    tesseractLanguages,
                    "--psm",
                    "6",
                    "tsv"
            ).redirectErrorStream(true).start();

            String processOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("OCR не выполнен: " + processOutput.trim());
            }
            return processOutput;
        } finally {
            Files.deleteIfExists(imagePath);
            Files.deleteIfExists(Path.of(outputBasePath + ".txt"));
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".png";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private List<BrokerHolding> parseHoldings(User user, String text) {
        List<OcrLine> lines = parseOcrLines(text);
        List<BrokerHolding> holdings = new ArrayList<>();
        String section = null;

        for (int index = 0; index < lines.size(); index++) {
            OcrLine ocrLine = lines.get(index);
            String line = ocrLine.text();
            String lowerLine = line.toLowerCase(Locale.ROOT);
            if (lowerLine.contains("облига")) {
                section = "Облигации";
                continue;
            }
            if (lowerLine.contains("валют")) {
                section = "Валюта";
                continue;
            }

            Matcher tickerMatcher = TICKER_PATTERN.matcher(normalizeTickerText(line));
            if (section == null) {
                continue;
            }

            BrokerHolding holding = new BrokerHolding();
            holding.setUser(user);
            holding.setHoldingType(section);

            if ("Облигации".equals(section) && index + 1 < lines.size()
                    && TICKER_PATTERN.matcher(normalizeTickerText(lines.get(index + 1).text())).find()) {
                OcrLine detailsLine = lines.get(index + 1);
                Matcher detailsTickerMatcher = TICKER_PATTERN.matcher(normalizeTickerText(detailsLine.text()));
                detailsTickerMatcher.find();
                holding.setTicker(normalizeTickerText(detailsTickerMatcher.group()));
                holding.setName(textInColumn(ocrLine, 0, 0.42));
                fillFromColumns(holding, ocrLine, detailsLine);
                holdings.add(holding);
                index++;
                continue;
            }

            if (tickerMatcher.find()) {
                holding.setTicker(normalizeTickerText(tickerMatcher.group()));
                holding.setName(textInColumn(ocrLine, 0, 0.42));
            } else if ("Валюта".equals(section) && lowerLine.contains("доллар")) {
                holding.setTicker("USD");
                holding.setName("Доллар США");
            } else if ("Валюта".equals(section) && lowerLine.contains("рубл")) {
                holding.setTicker("RUB");
                holding.setName("Российский рубль");
            } else {
                continue;
            }
            OcrLine detailsLine = index + 1 < lines.size() ? lines.get(index + 1) : null;
            fillFromColumns(holding, ocrLine, detailsLine);
            holdings.add(holding);
        }

        return holdings;
    }

    private List<OcrLine> parseOcrLines(String tsv) {
        Map<String, List<OcrWord>> wordsByLine = new LinkedHashMap<>();
        int pageWidth = 1;

        for (String row : tsv.lines().skip(1).toList()) {
            String[] cells = row.split("\t", 12);
            if (cells.length < 11) {
                continue;
            }
            if ("1".equals(cells[0])) {
                pageWidth = parseInt(cells[8], 1);
                continue;
            }
            if (cells.length < 12) {
                continue;
            }
            if (cells[11].isBlank()) {
                continue;
            }
            if (!"5".equals(cells[0])) {
                continue;
            }

            String key = cells[2] + ":" + cells[3] + ":" + cells[4];
            wordsByLine.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new OcrWord(
                            cleanLine(cells[11]),
                            parseInt(cells[6], 0),
                            parseInt(cells[7], 0),
                            parseInt(cells[8], 0)
                    ));
        }

        int finalPageWidth = pageWidth;
        return wordsByLine.values().stream()
                .map(words -> new OcrLine(words, finalPageWidth))
                .sorted(Comparator.comparingInt(OcrLine::top))
                .filter(line -> !line.text().isBlank())
                .toList();
    }

    private void fillFromColumns(BrokerHolding holding, OcrLine mainLine, OcrLine detailsLine) {
        holding.setPriceText(textInColumn(mainLine, 0.42, 0.64));
        holding.setValueText(textInColumn(mainLine, 0.64, 0.82));
        holding.setProfitText(textInColumn(mainLine, 0.82, 1));
        holding.setProfitAmount(parseDecimal(holding.getProfitText()));

        if (detailsLine != null && Math.abs(detailsLine.top() - mainLine.top()) < 70) {
            holding.setQuantityText(textInColumn(detailsLine, 0.64, 0.82));
            holding.setProfitPercent(parseDecimal(textInColumn(detailsLine, 0.82, 1)));
        }
    }

    private String textInColumn(OcrLine line, double from, double to) {
        int min = (int) (line.pageWidth() * from);
        int max = (int) (line.pageWidth() * to);
        return line.words().stream()
                .filter(word -> word.center() >= min && word.center() < max)
                .map(OcrWord::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
                .replace(" P", " ₽")
                .replace("P", "₽")
                .replace("Р", "₽")
                .trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String findName(List<String> lines, int tickerIndex) {
        for (int index = tickerIndex - 1; index >= 0; index--) {
            String candidate = lines.get(index);
            String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
            if (lowerCandidate.contains("название") || lowerCandidate.contains("цена") || lowerCandidate.contains("стоимость")
                    || lowerCandidate.contains("облига") || lowerCandidate.contains("валют")) {
                continue;
            }
            if (!TICKER_PATTERN.matcher(normalizeTickerText(candidate)).find()) {
                Matcher moneyMatcher = MONEY_PATTERN.matcher(candidate);
                return moneyMatcher.find() ? candidate.substring(0, moneyMatcher.start()).trim() : candidate;
            }
        }
        return "Без названия";
    }

    private void fillNumbers(BrokerHolding holding, List<String> lines, int tickerIndex) {
        List<String> moneyValues = new ArrayList<>();
        String quantity = null;
        String percent = null;

        int startIndex = Math.max(0, tickerIndex - 1);
        for (int index = startIndex; index < Math.min(lines.size(), tickerIndex + 8); index++) {
            String line = lines.get(index);
            String lowerLine = line.toLowerCase(Locale.ROOT);
            if (index > tickerIndex && (lowerLine.contains("облига") || lowerLine.contains("валют"))) {
                break;
            }
            if (index > tickerIndex && TICKER_PATTERN.matcher(normalizeTickerText(line)).find()) {
                break;
            }

            Matcher moneyMatcher = MONEY_PATTERN.matcher(line);
            while (moneyMatcher.find()) {
                moneyValues.add(moneyMatcher.group().replace("Р", "₽").replace("P", "₽"));
            }

            if (quantity == null && (line.contains("шт") || line.contains("$"))) {
                quantity = line;
            }

            Matcher percentMatcher = PERCENT_PATTERN.matcher(line);
            if (percentMatcher.find()) {
                percent = percentMatcher.group();
            }
        }

        if (!moneyValues.isEmpty()) {
            holding.setPriceText(moneyValues.get(0));
        }
        if (moneyValues.size() > 1) {
            holding.setValueText(moneyValues.get(1));
        }
        if (moneyValues.size() > 2) {
            String profitText = moneyValues.get(moneyValues.size() - 1);
            holding.setProfitText(profitText);
            holding.setProfitAmount(parseDecimal(profitText));
        }
        holding.setQuantityText(quantity);
        holding.setProfitPercent(parseDecimal(percent));
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value
                .replace("₽", "")
                .replace("%", "")
                .replace(" ", "")
                .replace(",", ".")
                .trim();
        if (normalizedValue.isBlank() || "—".equals(normalizedValue)) {
            return null;
        }
        return new BigDecimal(normalizedValue);
    }

    private String cleanLine(String line) {
        return line
                .replace("→", "->")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTickerText(String value) {
        return value
                .replace(" ", "")
                .replace('А', 'A')
                .replace('В', 'B')
                .replace('Е', 'E')
                .replace('К', 'K')
                .replace('М', 'M')
                .replace('Н', 'H')
                .replace('О', 'O')
                .replace('Р', 'P')
                .replace('С', 'C')
                .replace('Т', 'T')
                .replace('Х', 'X')
                .replace('а', 'A')
                .replace('в', 'B')
                .replace('е', 'E')
                .replace('к', 'K')
                .replace('м', 'M')
                .replace('н', 'H')
                .replace('о', 'O')
                .replace('р', 'P')
                .replace('с', 'C')
                .replace('т', 'T')
                .replace('х', 'X')
                .toUpperCase(Locale.ROOT);
    }

    private record OcrWord(String text, int left, int top, int width) {
        int center() {
            return left + width / 2;
        }
    }

    private record OcrLine(List<OcrWord> words, int pageWidth) {
        int top() {
            return words.stream().mapToInt(OcrWord::top).min().orElse(0);
        }

        String text() {
            return words.stream()
                    .sorted(Comparator.comparingInt(OcrWord::left))
                    .map(OcrWord::text)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
        }
    }

    public record ImportResult(int importedCount, String recognizedText) {
    }
}
