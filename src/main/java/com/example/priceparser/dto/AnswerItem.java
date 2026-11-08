package com.example.priceparser.dto;

import com.example.priceparser.model.PriceRecord;
import com.example.priceparser.model.PublishedAtSource;

import java.math.BigDecimal;
import java.time.Instant;

public record AnswerItem(
        Long id,
        String url,
        String title,
        String description,
        BigDecimal price,
        String currency,
        Instant publishedAt,
        PublishedAtSource publishedAtSource,
        Instant parsedAt,
        String threadName
) {

    public static AnswerItem from(PriceRecord record) {
        return new AnswerItem(
                record.getId(),
                record.getUrl(),
                record.getTitle(),
                record.getDescription(),
                record.getPrice(),
                record.getCurrency(),
                record.getPublishedAt(),
                record.getPublishedAtSource(),
                record.getParsedAt(),
                record.getThreadName());
    }
}
