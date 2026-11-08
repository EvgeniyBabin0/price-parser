package com.example.priceparser.service;

import com.example.priceparser.client.FetchedPage;
import com.example.priceparser.client.PageFetchException;
import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.ParseResult;
import com.example.priceparser.dto.ParserStatus;
import com.example.priceparser.dto.ResultLogEntry;
import com.example.priceparser.model.ParseStatus;
import com.example.priceparser.parser.ExtractedProduct;
import com.example.priceparser.parser.PageExtractor;
import com.example.priceparser.parser.PageParseException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ParsingService {

    private static final Logger log = LoggerFactory.getLogger(ParsingService.class);

    private final PageFetcher fetcher;
    private final PageExtractor extractor;
    private final ParseResultWriter writer;
    private final ResultFileLog fileLog;
    private final ThreadPoolExecutor executor;
    private final ParserProperties properties;

    /** Ссылки в работе: одну страницу не обрабатывают одновременно планировщик и POST-запрос. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    /** Ограничение одновременных запросов к одному сайту. */
    private final ConcurrentMap<String, Semaphore> hostLimits = new ConcurrentHashMap<>();

    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong scheduledRuns = new AtomicLong();
    private final AtomicReference<Instant> lastScheduledRunAt = new AtomicReference<>();

    public ParsingService(PageFetcher fetcher, PageExtractor extractor, ParseResultWriter writer, ResultFileLog fileLog,
                          ThreadPoolExecutor parserExecutor, ParserProperties properties) {
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.writer = writer;
        this.fileLog = fileLog;
        this.executor = parserExecutor;
        this.properties = properties;
    }

    /** Запускает обход параллельно и ждёт все результаты. Порядок результатов совпадает с порядком ссылок. */
    public List<ParseResult> parseAll(Collection<String> urls) {
        List<CompletableFuture<ParseResult>> futures = urls.stream().map(this::submit).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public void recordScheduledRun() {
        scheduledRuns.incrementAndGet();
        lastScheduledRunAt.set(Instant.now());
    }

    public ParserStatus status() {
        return new ParserStatus(
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount(),
                succeeded.get(),
                failed.get(),
                skipped.get(),
                scheduledRuns.get(),
                lastScheduledRunAt.get(),
                inFlight.size());
    }

    private CompletableFuture<ParseResult> submit(String url) {
        return CompletableFuture
                .supplyAsync(() -> parseOne(url), executor)
                .completeOnTimeout(ParseResult.timedOut(url), properties.taskTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private ParseResult parseOne(String url) {
        if (!inFlight.add(url)) {
            skipped.incrementAndGet();
            return ParseResult.skipped(url);
        }
        try {
            ExtractedProduct product = extractor.extract(fetchWithHostLimit(url));
            ParseResult result = ParseResult.success(
                    url, writer.saveSuccess(url, product, Thread.currentThread().getName()));
            succeeded.incrementAndGet();
            fileLog.append(new ResultLogEntry(Instant.now(), url, ParseStatus.SUCCESS, product.title(), product.price(),
                    product.currency(), null, Thread.currentThread().getName()));
            log.info("Обработана ссылка {}: {} {}", url, product.price(), product.currency());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(url, "Обработка прервана");
        } catch (PageFetchException | PageParseException e) {
            return fail(url, e.getMessage());
        } catch (RuntimeException e) {
            log.debug("Ошибка обработки {}", url, e);
            return fail(url, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            inFlight.remove(url);
        }
    }

    private FetchedPage fetchWithHostLimit(String url) throws InterruptedException {
        Semaphore limit = hostLimits.computeIfAbsent(hostKey(url),
                host -> new Semaphore(properties.maxConcurrentPerHost(), true));
        limit.acquire();
        try {
            return fetcher.fetch(url);
        } finally {
            limit.release();
        }
    }

    private static String hostKey(String url) {
        return URI.create(url).getHost().toLowerCase(Locale.ROOT);
    }

    private ParseResult fail(String url, String error) {
        failed.incrementAndGet();
        log.warn("Не удалось обработать {}: {}", url, error);
        fileLog.append(new ResultLogEntry(Instant.now(), url, ParseStatus.FAILED, null, null, null, error,
                Thread.currentThread().getName()));
        writer.saveFailure(url, error);
        return ParseResult.failed(url, error);
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
