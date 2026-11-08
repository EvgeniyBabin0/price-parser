package com.example.priceparser.service;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.ResultLogEntry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Собственное файловое хранилище результатов в формате JSON Lines. Потоки парсинга только кладут запись
 * в блокирующую очередь, а в файл её пишет отдельный демон-поток, поэтому медленный диск не задерживает обход.
 */
@Component
public class ResultFileLog implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ResultFileLog.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final int QUEUE_CAPACITY = 10_000;
    private static final int MAX_BATCH_SIZE = 100;

    private final Path file;
    private final BlockingQueue<ResultLogEntry> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    /** Защищает файл: чтение не должно видеть наполовину записанную пачку строк. */
    private final ReentrantLock fileLock = new ReentrantLock();
    private final Thread writerThread;

    private volatile boolean running = true;

    public ResultFileLog(ParserProperties properties) {
        this.file = Path.of(properties.resultsFile()).toAbsolutePath();
        this.writerThread = new Thread(this, "result-file-writer");
        this.writerThread.setDaemon(true);
    }

    @PostConstruct
    public void start() {
        writerThread.start();
        log.info("Результаты записываются в файл {}", file);
    }

    public Thread writerThread() {
        return writerThread;
    }

    /** Не блокирует вызывающий поток: при переполненной очереди запись отбрасывается с предупреждением. */
    public void append(ResultLogEntry entry) {
        if (!queue.offer(entry)) {
            log.warn("Очередь записи в файл переполнена, запись по {} отброшена", entry.url());
        }
    }

    @Override
    public void run() {
        // Поток не прерывается через interrupt: прерывание закрыло бы файловый канал посреди записи
        while (running || !queue.isEmpty()) {
            try {
                ResultLogEntry first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first != null) {
                    List<ResultLogEntry> batch = new ArrayList<>();
                    batch.add(first);
                    queue.drainTo(batch, MAX_BATCH_SIZE - 1);
                    write(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Последние записи файла, сначала самые новые. */
    public List<ResultLogEntry> readLast(int limit) {
        List<String> lines;
        fileLock.lock();
        try {
            lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл " + file, e);
        } finally {
            fileLock.unlock();
        }
        List<ResultLogEntry> entries = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && entries.size() < limit; i--) {
            parseLine(lines.get(i)).ifPresent(entries::add);
        }
        return entries;
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        running = false;
        writerThread.join(TimeUnit.SECONDS.toMillis(5));
    }

    private void write(List<ResultLogEntry> batch) {
        StringBuilder text = new StringBuilder();
        batch.forEach(entry -> text.append(JSON.writeValueAsString(entry)).append(System.lineSeparator()));
        fileLock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Не удалось записать результаты в файл {}: {}", file, e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }

    private static Optional<ResultLogEntry> parseLine(String line) {
        try {
            return Optional.of(JSON.readValue(line, ResultLogEntry.class));
        } catch (JacksonException e) {
            // Строка могла оборваться при аварийном завершении приложения
            return Optional.empty();
        }
    }
}
