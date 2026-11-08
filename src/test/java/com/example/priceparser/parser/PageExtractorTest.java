package com.example.priceparser.parser;

import com.example.priceparser.client.FetchedPage;
import com.example.priceparser.model.PublishedAtSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageExtractorTest {

    private final PageExtractor extractor = new PageExtractor();

    private static FetchedPage page(String html, Instant lastModified) {
        return new FetchedPage("https://shop.test/item", html.getBytes(StandardCharsets.UTF_8), "UTF-8", lastModified);
    }

    @Test
    void readsJsonLdWithGraphAndOfferArray() {
        String html = """
                <html><head><title>Не это</title><script type="application/ld+json">
                {"@graph":[{"@type":"WebPage","datePublished":"2026-02-03T10:00:00Z"},
                {"@type":["Thing","Product"],"name":"Товар","description":"Описание",
                 "offers":[{"lowPrice":1999.5,"priceCurrency":"RUB"}]}]}
                </script></head></html>""";

        ExtractedProduct product = extractor.extract(page(html, null));

        assertThat(product.title()).isEqualTo("Товар");
        assertThat(product.description()).isEqualTo("Описание");
        assertThat(product.price()).isEqualByComparingTo("1999.50");
        assertThat(product.currency()).isEqualTo("RUB");
        assertThat(product.publishedAt()).isEqualTo(Instant.parse("2026-02-03T10:00:00Z"));
        assertThat(product.publishedAtSource()).isEqualTo(PublishedAtSource.PAGE);
    }

    @Test
    void readsOpenGraphAndSurvivesBrokenJsonLd() {
        String html = """
                <html><head><script type="application/ld+json">{ broken</script>
                <meta property="og:title" content="OG товар">
                <meta property="og:description" content="OG описание">
                <meta property="product:price:amount" content="100.50">
                <meta property="product:price:currency" content="USD">
                <meta property="article:published_time" content="2026-01-01T12:00:00+03:00">
                </head></html>""";

        ExtractedProduct product = extractor.extract(page(html, null));

        assertThat(product.title()).isEqualTo("OG товар");
        assertThat(product.price()).isEqualByComparingTo("100.50");
        assertThat(product.currency()).isEqualTo("USD");
        assertThat(product.publishedAt()).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
    }

    @Test
    void readsPlainHtmlAndSkipsOldPrice() {
        String html = """
                <html><body><h1>Товар</h1><p>Первый абзац</p>
                <span class="old-price">2 000 ₽</span><span class="price">1 500 ₽</span>
                <time datetime="2025-12-31">31 декабря</time></body></html>""";

        ExtractedProduct product = extractor.extract(page(html, null));

        assertThat(product.title()).isEqualTo("Товар");
        assertThat(product.description()).isEqualTo("Первый абзац");
        assertThat(product.price()).isEqualByComparingTo("1500");
        assertThat(product.currency()).isEqualTo("RUB");
        assertThat(product.publishedAt()).isEqualTo(Instant.parse("2025-12-31T00:00:00Z"));
    }

    @Test
    void fallsBackToLastModifiedHeader() {
        Instant lastModified = Instant.parse("2026-07-07T07:07:07Z");
        String html = "<html><body><h1>Товар</h1><span class=\"price\">10 $</span></body></html>";

        ExtractedProduct product = extractor.extract(page(html, lastModified));

        assertThat(product.publishedAt()).isEqualTo(lastModified);
        assertThat(product.publishedAtSource()).isEqualTo(PublishedAtSource.HTTP_LAST_MODIFIED);
    }

    @Test
    void leavesDateEmptyWhenUnknown() {
        String html = "<html><body><h1>Товар</h1><span class=\"price\">10 $</span></body></html>";

        ExtractedProduct product = extractor.extract(page(html, null));

        assertThat(product.publishedAt()).isNull();
        assertThat(product.publishedAtSource()).isNull();
    }

    @Test
    void failsWhenPriceIsMissing() {
        assertThatThrownBy(() -> extractor.extract(page("<html><body><h1>Товар</h1></body></html>", null)))
                .isInstanceOf(PageParseException.class)
                .hasMessageContaining("цена");
    }
}
