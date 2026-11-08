package com.example.priceparser.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбирает текст цены в разных форматах: "£51.77", "1 299,00 ₽", "$1,299.99", "1.299,00 EUR". */
public final class PriceTextParser {

    /** Текст длиннее этого предела считается описанием, а не ценой. */
    private static final int MAX_PRICE_TEXT_LENGTH = 60;
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s\\u00A0\\u202F.,']*\\d|\\d");
    private static final Pattern ISO_CODE = Pattern.compile("\\b[A-Z]{3}\\b");
    private static final Set<String> KNOWN_CODES = Set.of("RUB", "USD", "EUR", "GBP", "JPY", "CNY", "KZT", "BYN");

    private PriceTextParser() {
    }

    public record ParsedPrice(BigDecimal amount, String currency) {
    }

    public static Optional<ParsedPrice> parse(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_PRICE_TEXT_LENGTH) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(normalize(matcher.group())).setScale(2, RoundingMode.HALF_UP);
            return amount.signum() > 0
                    ? Optional.of(new ParsedPrice(amount, detectCurrency(text)))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String normalize(String raw) {
        String number = raw.replaceAll("[\\s\\u00A0\\u202F']", "");
        int lastDot = number.lastIndexOf('.');
        int lastComma = number.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            // Оба разделителя: дробным считается последний
            char decimal = lastDot > lastComma ? '.' : ',';
            char grouping = decimal == '.' ? ',' : '.';
            return number.replace(String.valueOf(grouping), "").replace(decimal, '.');
        }
        int separatorIndex = Math.max(lastDot, lastComma);
        if (separatorIndex < 0) {
            return number;
        }
        char separator = number.charAt(separatorIndex);
        boolean repeated = number.indexOf(separator) != separatorIndex;
        boolean thousandsGroup = number.length() - separatorIndex - 1 == 3;
        if (repeated || thousandsGroup) {
            return number.replace(String.valueOf(separator), "");
        }
        return number.replace(separator, '.');
    }

    private static String detectCurrency(String text) {
        Matcher code = ISO_CODE.matcher(text);
        while (code.find()) {
            if (KNOWN_CODES.contains(code.group())) {
                return code.group();
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("₽") || lower.contains("руб")) {
            return "RUB";
        }
        if (lower.contains("$")) {
            return "USD";
        }
        if (lower.contains("€")) {
            return "EUR";
        }
        if (lower.contains("£")) {
            return "GBP";
        }
        if (lower.contains("₸")) {
            return "KZT";
        }
        return null;
    }
}
