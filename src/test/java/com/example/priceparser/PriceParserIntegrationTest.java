package com.example.priceparser;

import com.example.priceparser.web.DemoShopController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"parser.seed-demo-urls=false", "parser.schedule-initial-delay=1h",
                "parser.results-file=target/test-parse-results.jsonl"})
class PriceParserIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    private String productUrl(long id) {
        return "http://localhost:" + port + "/demo-shop/products/" + id;
    }

    private JsonNode parse(List<String> urls) {
        return client.post().uri("/api/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("urls", urls))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode answer(String query) {
        return client.get().uri("/answer" + query).retrieve().body(JsonNode.class);
    }

    @Test
    void parsesAllDemoMarkupKindsAndSortsResults() {
        List<String> urls = DemoShopController.productIds().stream().map(this::productUrl).toList();

        JsonNode results = parse(urls);

        assertThat(results).hasSize(urls.size());
        Set<String> threads = new HashSet<>();
        for (JsonNode result : results) {
            assertThat(result.path("status").asString()).isEqualTo("SUCCESS");
            JsonNode product = result.path("product");
            assertThat(product.path("title").asString()).isNotBlank();
            assertThat(product.path("description").asString()).isNotBlank();
            assertThat(product.path("publishedAt").asString()).isNotBlank();
            assertThat(product.path("publishedAtSource").asString()).isEqualTo("PAGE");
            threads.add(product.path("threadName").asString());
        }
        assertThat(threads).allMatch(name -> name.startsWith("parser-worker-"));
        assertThat(threads.size()).isGreaterThan(1);

        JsonNode page = answer("?sortBy=price&direction=asc&size=100");
        List<BigDecimal> prices = new ArrayList<>();
        page.path("content").forEach(item -> prices.add(item.path("price").decimalValue()));
        assertThat(prices).hasSizeGreaterThanOrEqualTo(urls.size()).isSorted();
    }

    @Test
    void paginatesFiltersAndKeepsHistory() {
        String url = productUrl(3);
        parse(List.of(url));
        parse(List.of(url));

        String byUrl = "?url=" + url;
        assertThat(answer(byUrl).path("totalElements").asInt()).isEqualTo(1);
        assertThat(answer(byUrl + "&latestOnly=false").path("totalElements").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode firstPage = answer(byUrl + "&latestOnly=false&size=1&page=0");
        assertThat(firstPage.path("content")).hasSize(1);
        assertThat(firstPage.path("totalPages").asInt()).isGreaterThanOrEqualTo(2);

        assertThat(answer("?q=наушники&currency=usd").path("totalElements").asInt()).isEqualTo(1);
        assertThat(answer("?q=наушники&maxPrice=1").path("totalElements").asInt()).isZero();
        assertThat(answer("?page=999").path("content")).isEmpty();
    }

    @Test
    void reportsBadUrlsAndPageErrorsPerUrl() {
        String missing = productUrl(999);
        String noPrice = "http://localhost:" + port + "/demo-shop/no-price";

        JsonNode results = parse(List.of("not a url", "ftp://example.com/file", missing, noPrice));

        assertThat(results.get(0).path("status").asString()).isEqualTo("REJECTED");
        assertThat(results.get(1).path("status").asString()).isEqualTo("REJECTED");
        assertThat(results.get(2).path("status").asString()).isEqualTo("FAILED");
        assertThat(results.get(2).path("error").asString()).isEqualTo("HTTP 404");
        assertThat(results.get(3).path("status").asString()).isEqualTo("FAILED");
        assertThat(results.get(3).path("error").asString()).contains("цена");

        JsonNode tracked = client.get().uri("/api/urls").retrieve().body(JsonNode.class);
        boolean failureRecorded = false;
        for (JsonNode item : tracked) {
            failureRecorded |= item.path("url").asString().equals(missing)
                    && item.path("lastStatus").asString().equals("FAILED");
        }
        assertThat(failureRecorded).isTrue();
    }

    @Test
    void writesResultsToFileInBackground() {
        String url = productUrl(2);
        parse(List.of(url));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            JsonNode entries = client.get().uri("/api/results-file?limit=1000").retrieve().body(JsonNode.class);
            boolean found = false;
            for (JsonNode entry : entries) {
                found |= entry.path("url").asString().equals(url)
                        && entry.path("status").asString().equals("SUCCESS")
                        && entry.path("title").asString().equals("Смартфон Nova X");
            }
            assertThat(found).isTrue();
        });
    }

    @Test
    void declaresUtf8InJsonResponses() {
        MediaType contentType = client.get().uri("/answer").retrieve().toBodilessEntity().getHeaders().getContentType();

        assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void rejectsInvalidRequests() {
        assertThatThrownBy(() -> answer("?sortBy=unknown")).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> answer("?direction=up")).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> answer("?size=0")).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> parse(List.of())).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> client.get().uri("/api/results-file?limit=0").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }
}
