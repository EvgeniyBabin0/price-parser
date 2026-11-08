package com.example.priceparser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("parser")
public record ParserProperties(
        int poolSize,
        int queueCapacity,
        int maxConcurrentPerHost,
        Duration scheduleInitialDelay,
        Duration scheduleFixedDelay,
        String resultsFile,
        Duration connectTimeout,
        Duration readTimeout,
        Duration taskTimeout,
        int maxPageSizeBytes,
        String userAgent,
        boolean seedDemoUrls
) {
}
