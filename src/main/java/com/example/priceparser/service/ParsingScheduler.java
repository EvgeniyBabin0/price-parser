package com.example.priceparser.service;

import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.ParseResult;
import com.example.priceparser.model.ParseStatus;
import com.example.priceparser.model.TrackedUrl;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Планировщик на ScheduledExecutorService: периодически отправляет все отслеживаемые ссылки в пул парсинга. */
@Component
public class ParsingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParsingScheduler.class);

    private final TrackedUrlService trackedUrls;
    private final ParsingService parsingService;
    private final ParserProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "parser-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public ParsingScheduler(TrackedUrlService trackedUrls, ParsingService parsingService, ParserProperties properties) {
        this.trackedUrls = trackedUrls;
        this.parsingService = parsingService;
        this.properties = properties;
    }

    /** Fixed delay: следующий обход начнётся только через заданное время после завершения текущего. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::runSafely,
                properties.scheduleInitialDelay().toMillis(),
                properties.scheduleFixedDelay().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    /** Исключение, вышедшее из задачи, навсегда отменило бы все следующие запуски, поэтому оно перехватывается. */
    private void runSafely() {
        try {
            parseTrackedUrls();
        } catch (RuntimeException e) {
            log.error("Обход по расписанию завершился ошибкой", e);
        }
    }

    private void parseTrackedUrls() {
        parsingService.recordScheduledRun();
        List<String> urls = trackedUrls.findAll().stream().map(TrackedUrl::getUrl).toList();
        if (urls.isEmpty()) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        List<ParseResult> results = parsingService.parseAll(urls);
        long successful = results.stream().filter(result -> result.status() == ParseStatus.SUCCESS).count();
        log.info("Обход по расписанию: успешно {} из {} за {} мс",
                successful, results.size(), System.currentTimeMillis() - startedAt);
    }
}
