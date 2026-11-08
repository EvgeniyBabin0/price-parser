package com.example.priceparser.service;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.ParseResult;
import com.example.priceparser.model.TrackedUrl;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParsingSchedulerTest {

    @Test
    void keepsRunningAfterFailedRun() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        CountDownLatch parsedTwice = new CountDownLatch(2);

        TrackedUrlService trackedUrls = new TrackedUrlService(null) {
            @Override
            public List<TrackedUrl> findAll() {
                if (lookups.incrementAndGet() == 1) {
                    throw new IllegalStateException("Сбой первого запуска");
                }
                return List.of(new TrackedUrl("http://a.test/1", Instant.now()));
            }
        };
        ParsingService parsingService = new ParsingService(null, null, null, null, null, null) {
            @Override
            public List<ParseResult> parseAll(Collection<String> urls) {
                parsedTwice.countDown();
                return List.of();
            }
        };
        ParserProperties properties = new ParserProperties(1, 1, 1, Duration.ofMillis(1), Duration.ofMillis(10),
                "unused.jsonl", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 1024, "test", false);

        ParsingScheduler scheduler = new ParsingScheduler(trackedUrls, parsingService, properties);
        scheduler.start();
        try {
            assertThat(parsedTwice.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            scheduler.stop();
        }
    }
}
