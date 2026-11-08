package com.example.priceparser.parser;

import com.example.priceparser.model.PublishedAtSource;

import java.math.BigDecimal;
import java.time.Instant;

public record ExtractedProduct(
        String title,
        String description,
        BigDecimal price,
        String currency,
        Instant publishedAt,
        PublishedAtSource publishedAtSource
) {
}
