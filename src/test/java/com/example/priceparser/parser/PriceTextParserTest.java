package com.example.priceparser.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTextParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "£51.77        | 51.77   | GBP",
            "1 299,00 ₽    | 1299.00 | RUB",
            "$1,299.99     | 1299.99 | USD",
            "1.299,00 EUR  | 1299.00 | EUR",
            "1,299         | 1299.00 | ",
            "2 500 руб.    | 2500.00 | RUB",
            "1.000.000     | 1000000.00 | ",
            "49            | 49.00   | ",
            "от 99,9 €     | 99.90   | EUR"
    })
    void parsesCommonFormats(String text, BigDecimal amount, String currency) {
        PriceTextParser.ParsedPrice price = PriceTextParser.parse(text).orElseThrow();

        assertThat(price.amount()).isEqualByComparingTo(amount);
        assertThat(price.currency()).isEqualTo(currency);
    }

    @Test
    void parsesNonBreakingSpaceSeparator() {
        assertThat(PriceTextParser.parse("12 990 ₽").orElseThrow().amount())
                .isEqualByComparingTo("12990");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Нет в наличии", "0", "0.00 ₽",
            "Очень длинный текст с описанием товара, в котором случайно встретилось число 15"})
    void rejectsTextWithoutPrice(String text) {
        assertThat(PriceTextParser.parse(text)).isEmpty();
    }
}
