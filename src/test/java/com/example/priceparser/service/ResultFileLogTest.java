package com.example.priceparser.service;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.ResultLogEntry;
import com.example.priceparser.model.ParseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFileLogTest {

    @TempDir
    Path tempDir;

    private ResultFileLog fileLog(Path file) {
        return new ResultFileLog(new ParserProperties(1, 1, 1, Duration.ofHours(1), Duration.ofHours(1), file.toString(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 1024, "test", false));
    }

    private static ResultLogEntry entry(String url) {
        return new ResultLogEntry(Instant.parse("2026-01-01T00:00:00Z"), url, ParseStatus.SUCCESS, "Товар",
                new BigDecimal("10.50"), "RUB", null, "parser-worker-1");
    }

    @Test
    void writerIsDaemonThread() {
        assertThat(fileLog(tempDir.resolve("log.jsonl")).writerThread().isDaemon()).isTrue();
    }

    @Test
    void stopWritesAllEntriesFromConcurrentProducers() throws Exception {
        Path file = tempDir.resolve("nested/log.jsonl");
        ResultFileLog log = fileLog(file);
        log.start();

        List<Thread> producers = new ArrayList<>();
        for (int producer = 0; producer < 4; producer++) {
            int id = producer;
            Thread thread = new Thread(() -> IntStream.range(0, 250).forEach(i -> log.append(entry("http://a.test/" + id + "/" + i))));
            producers.add(thread);
            thread.start();
        }
        for (Thread thread : producers) {
            thread.join();
        }
        log.stop();

        assertThat(log.writerThread().isAlive()).isFalse();
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSize(1000).doesNotHaveDuplicates();
    }

    @Test
    void readsNewestFirstAndSkipsCorruptedLines() throws Exception {
        Path file = tempDir.resolve("log.jsonl");
        ResultFileLog log = fileLog(file);
        log.start();
        log.append(entry("http://a.test/1"));
        log.append(entry("http://a.test/2"));
        log.stop();
        Files.writeString(file, "{\"оборванная", java.nio.file.StandardOpenOption.APPEND);

        List<ResultLogEntry> entries = log.readLast(10);

        assertThat(entries).extracting(ResultLogEntry::url).containsExactly("http://a.test/2", "http://a.test/1");
        assertThat(entries.get(0).price()).isEqualByComparingTo("10.50");
        assertThat(log.readLast(1)).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenFileDoesNotExist() {
        assertThat(fileLog(tempDir.resolve("missing.jsonl")).readLast(10)).isEmpty();
    }
}
