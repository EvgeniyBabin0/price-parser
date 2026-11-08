package com.example.priceparser.service;

import com.example.priceparser.client.FetchedPage;
import com.example.priceparser.client.PageFetcher;
import com.example.priceparser.config.ParserProperties;
import com.example.priceparser.dto.AnswerItem;
import com.example.priceparser.dto.ParseResult;
import com.example.priceparser.model.ParseStatus;
import com.example.priceparser.parser.ExtractedProduct;
import com.example.priceparser.parser.PageExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParsingServiceTest {

    private static final String HTML = "<html><body><h1>Товар</h1><span class=\"price\">10 $</span></body></html>";

    private final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10));

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private static ParserProperties properties(int maxPerHost) {
        return new ParserProperties(4, 10, maxPerHost, Duration.ofHours(1), Duration.ofHours(1), "unused.jsonl",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(10), 1024, "test", false);
    }

    private ParsingService service(PageFetcher fetcher, int maxPerHost) {
        // Поток записи в файл здесь не запускается: записи остаются в очереди
        ResultFileLog fileLog = new ResultFileLog(properties(maxPerHost));
        return new ParsingService(fetcher, new PageExtractor(), new NoOpWriter(), fileLog, executor, properties(maxPerHost));
    }

    @Test
    void parsesDifferentUrlsOnDifferentThreads() {
        // Каждая загрузка ждёт, пока начнутся все четыре: пройти можно только при реальной параллельности
        CountDownLatch allStarted = new CountDownLatch(4);
        Set<String> threads = ConcurrentHashMap.newKeySet();
        PageFetcher fetcher = new StubFetcher(url -> {
            threads.add(Thread.currentThread().getName());
            allStarted.countDown();
            await(allStarted);
        });

        List<ParseResult> results = service(fetcher, 4).parseAll(List.of(
                "http://a.test/1", "http://b.test/2", "http://c.test/3", "http://d.test/4"));

        assertThat(results).extracting(ParseResult::status).containsOnly(ParseStatus.SUCCESS);
        assertThat(results).extracting(ParseResult::url)
                .containsExactly("http://a.test/1", "http://b.test/2", "http://c.test/3", "http://d.test/4");
        assertThat(threads).hasSize(4);
    }

    @Test
    void skipsUrlAlreadyInProgress() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ParsingService service = service(new StubFetcher(url -> {
            started.countDown();
            await(release);
        }), 4);

        CompletableFuture<List<ParseResult>> first =
                CompletableFuture.supplyAsync(() -> service.parseAll(List.of("http://a.test/1")));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        List<ParseResult> second = service.parseAll(List.of("http://a.test/1"));
        release.countDown();

        assertThat(second.get(0).status()).isEqualTo(ParseStatus.SKIPPED_IN_PROGRESS);
        assertThat(first.get(5, TimeUnit.SECONDS).get(0).status()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(service.status().skippedInProgress()).isEqualTo(1);
    }

    @Test
    void limitsConcurrentRequestsPerHost() {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        PageFetcher fetcher = new StubFetcher(url -> {
            peak.accumulateAndGet(current.incrementAndGet(), Math::max);
            sleep();
            current.decrementAndGet();
        });

        service(fetcher, 2).parseAll(List.of(
                "http://a.test/1", "http://a.test/2", "http://a.test/3", "http://a.test/4", "http://a.test/5"));

        assertThat(peak.get()).isEqualTo(2);
    }

    @Test
    void reportsFailureWithoutBreakingOtherUrls() {
        PageFetcher fetcher = new StubFetcher(url -> {
            if (url.endsWith("/bad")) {
                throw new IllegalStateException("Соединение разорвано");
            }
        });

        List<ParseResult> results = service(fetcher, 4).parseAll(List.of("http://a.test/bad", "http://a.test/ok"));

        assertThat(results.get(0).status()).isEqualTo(ParseStatus.FAILED);
        assertThat(results.get(0).error()).contains("Соединение разорвано");
        assertThat(results.get(1).status()).isEqualTo(ParseStatus.SUCCESS);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Не дождались других потоков");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface FetchAction {
        void run(String url);
    }

    private static final class StubFetcher extends PageFetcher {

        private final FetchAction action;

        StubFetcher(FetchAction action) {
            super(null, properties(4));
            this.action = action;
        }

        @Override
        public FetchedPage fetch(String url) {
            action.run(url);
            return new FetchedPage(url, HTML.getBytes(StandardCharsets.UTF_8), "UTF-8", null);
        }
    }

    private static final class NoOpWriter extends ParseResultWriter {

        NoOpWriter() {
            super(null, null);
        }

        @Override
        public AnswerItem saveSuccess(String url, ExtractedProduct product, String threadName) {
            return null;
        }

        @Override
        public void saveFailure(String url, String error) {
        }
    }
}
