package com.example.priceparser.dto;

import java.math.BigDecimal;

public record AnswerQuery(
        int page,
        int size,
        String sortBy,
        String direction,
        String q,
        String url,
        String currency,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean latestOnly
) {
}
