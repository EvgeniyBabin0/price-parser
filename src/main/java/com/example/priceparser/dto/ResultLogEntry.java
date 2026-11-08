package com.example.priceparser.dto;

import com.example.priceparser.model.ParseStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Строка файла результатов. */
public record ResultLogEntry(
        Instant time,
        String url,
        ParseStatus status,
        String title,
        BigDecimal price,
        String currency,
        String error,
        String threadName
) {
}
