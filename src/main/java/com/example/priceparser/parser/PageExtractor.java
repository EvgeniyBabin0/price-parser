package com.example.priceparser.parser;

import com.example.priceparser.client.FetchedPage;
import com.example.priceparser.model.PublishedAtSource;
import com.example.priceparser.parser.PriceTextParser.ParsedPrice;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Достаёт данные о товаре из HTML. Для каждого поля источники перебираются от самых надёжных к менее надёжным:
 * JSON-LD (schema.org), мета-теги Open Graph, микроразметка, затем обычные CSS-классы.
 */
@Component
public class PageExtractor {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String PRICE_SELECTORS =
            ".product_main .price_color, .price_color, .product-price, .price, [class*=price]:not([class*=old])";
    private static final List<DateTimeFormatter> EXTRA_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"));
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    public ExtractedProduct extract(FetchedPage page) {
        Document doc = parseHtml(page);
        JsonNode product = findJsonLdProduct(doc);

        String title = firstNonBlank(
                text(product, "name"),
                meta(doc, "meta[property=og:title]"),
                textOf(doc.selectFirst("[itemprop=name]")),
                textOf(doc.selectFirst("h1")),
                doc.title());
        if (title == null) {
            throw new PageParseException("Не найден заголовок товара");
        }

        ParsedPrice price = extractPrice(doc, product)
                .orElseThrow(() -> new PageParseException("Не найдена цена товара"));

        String description = firstNonBlank(
                text(product, "description"),
                meta(doc, "meta[property=og:description]"),
                meta(doc, "meta[name=description]"),
                textOf(doc.selectFirst("[itemprop=description]")),
                textOf(doc.selectFirst("#product_description + p")),
                textOf(doc.selectFirst("article p, main p, p")));

        Instant published = extractPublishedAt(doc);
        PublishedAtSource source = published != null ? PublishedAtSource.PAGE : null;
        if (published == null && page.lastModified() != null) {
            published = page.lastModified();
            source = PublishedAtSource.HTTP_LAST_MODIFIED;
        }

        return new ExtractedProduct(title, description, price.amount(), price.currency(), published, source);
    }

    private static Document parseHtml(FetchedPage page) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(page.body()), page.charset(), page.url());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<ParsedPrice> extractPrice(Document doc, JsonNode product) {
        Optional<ParsedPrice> fromJsonLd = priceFromJsonLd(product);
        if (fromJsonLd.isPresent()) {
            return fromJsonLd;
        }

        String metaCurrency = firstNonBlank(
                meta(doc, "meta[property=product:price:currency]"),
                meta(doc, "meta[property=og:price:currency]"),
                meta(doc, "[itemprop=priceCurrency]"));

        List<String> candidates = new ArrayList<>();
        candidates.add(meta(doc, "meta[property=product:price:amount]"));
        candidates.add(meta(doc, "meta[property=og:price:amount]"));
        candidates.add(meta(doc, "[itemprop=price]"));
        candidates.add(textOf(doc.selectFirst("[itemprop=price]")));
        doc.select(PRICE_SELECTORS).forEach(element -> candidates.add(textOf(element)));

        return candidates.stream()
                .map(PriceTextParser::parse)
                .flatMap(Optional::stream)
                .findFirst()
                .map(price -> new ParsedPrice(price.amount(), firstNonBlank(metaCurrency, price.currency())));
    }

    private Optional<ParsedPrice> priceFromJsonLd(JsonNode product) {
        if (product == null) {
            return Optional.empty();
        }
        JsonNode offers = product.path("offers");
        JsonNode offer = offers.isArray() && !offers.isEmpty() ? offers.get(0) : offers;
        String amount = firstNonBlank(text(offer, "price"), text(offer, "lowPrice"));
        return PriceTextParser.parse(amount).map(price -> new ParsedPrice(
                price.amount(), firstNonBlank(text(offer, "priceCurrency"), price.currency())));
    }

    private JsonNode findJsonLdProduct(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode product = findProductNode(JSON.readTree(script.data()));
                if (product != null) {
                    return product;
                }
            } catch (JacksonException ignored) {
                // Битый JSON-LD встречается часто: переходим к следующим источникам
            }
        }
        return null;
    }

    private JsonNode findProductNode(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findProductNode(item);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (isProductType(node.path("@type"))) {
            return node;
        }
        return node.has("@graph") ? findProductNode(node.get("@graph")) : null;
    }

    private static boolean isProductType(JsonNode type) {
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (isProductType(item)) {
                    return true;
                }
            }
            return false;
        }
        return type.isString() && type.asString().endsWith("Product");
    }

    private Instant extractPublishedAt(Document doc) {
        List<String> candidates = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode date = JSON.readTree(script.data()).findValue("datePublished");
                if (date != null && date.isString()) {
                    candidates.add(date.asString());
                }
            } catch (JacksonException ignored) {
                // См. findJsonLdProduct
            }
        }
        candidates.add(meta(doc, "meta[property=article:published_time]"));
        candidates.add(meta(doc, "meta[property=product:published_time]"));
        candidates.add(meta(doc, "meta[name=date]"));
        Element itemprop = doc.selectFirst("[itemprop=datePublished]");
        if (itemprop != null) {
            candidates.add(firstNonBlank(itemprop.attr("content"), itemprop.attr("datetime"), itemprop.text()));
        }
        Element time = doc.selectFirst("time[datetime]");
        if (time != null) {
            candidates.add(time.attr("datetime"));
        }

        return candidates.stream()
                .map(PageExtractor::parseDate)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    static Optional<Instant> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String date = value.strip();
        try {
            return Optional.of(OffsetDateTime.parse(date).toInstant());
        } catch (DateTimeParseException ignored) {
            // Пробуем следующий формат
        }
        for (DateTimeFormatter format : EXTRA_DATE_FORMATS) {
            try {
                return Optional.of(OffsetDateTime.parse(date, format).toInstant());
            } catch (DateTimeParseException ignored) {
                // Пробуем следующий формат
            }
        }
        try {
            return Optional.of(LocalDateTime.parse(date).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // Пробуем следующий формат
        }
        try {
            return Optional.of(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // Пробуем следующий формат
        }
        try {
            return Optional.of(LocalDate.parse(date, RU_DATE).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isString() || value.isNumber() ? value.asString() : null;
    }

    private static String meta(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        return element == null ? null : element.attr("content");
    }

    private static String textOf(Element element) {
        return element == null ? null : element.text();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip().replaceAll("\\s+", " ");
            }
        }
        return null;
    }
}
