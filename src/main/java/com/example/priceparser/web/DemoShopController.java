package com.example.priceparser.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Встроенный учебный магазин, который играет роль внешнего сервиса: проверка не зависит от интернета.
 * Страницы размечены тремя способами, цена слегка меняется при каждом запросе, а ответ приходит с задержкой,
 * как у настоящего сайта.
 */
@RestController
@RequestMapping("/demo-shop")
public class DemoShopController {

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct(1, "Ноутбук Aero 14", "Лёгкий ноутбук с экраном 14 дюймов и автономностью до 12 часов.", "84990.00", "RUB", "2026-03-12"),
            new DemoProduct(2, "Смартфон Nova X", "Смартфон с камерой 50 Мп и быстрой зарядкой.", "39990.00", "RUB", "2026-05-20"),
            new DemoProduct(3, "Наушники Silence Pro", "Беспроводные наушники с активным шумоподавлением.", "149.90", "USD", "2026-01-15"),
            new DemoProduct(4, "Кофемашина Barista 3", "Автоматическая кофемашина с капучинатором.", "45500.00", "RUB", "2025-11-02"),
            new DemoProduct(5, "Электросамокат City Go", "Городской самокат с запасом хода 30 км.", "379.00", "EUR", "2026-04-08"),
            new DemoProduct(6, "Монитор Vision 27", "Монитор 27 дюймов, разрешение QHD и частота 165 Гц.", "27990.00", "RUB", "2026-02-25"),
            new DemoProduct(7, "Клавиатура Keys Mini", "Компактная механическая клавиатура с подсветкой.", "89.50", "GBP", "2025-09-30"),
            new DemoProduct(8, "Робот-пылесос Clean R2", "Робот-пылесос с влажной уборкой и картой помещений.", "23490.00", "RUB", "2026-06-11"));

    public static List<Long> productIds() {
        return PRODUCTS.stream().map(DemoProduct::id).toList();
    }

    @GetMapping(value = "/products/{id}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String product(@PathVariable long id) throws InterruptedException {
        DemoProduct product = PRODUCTS.stream()
                .filter(candidate -> candidate.id() == id)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        Thread.sleep(ThreadLocalRandom.current().nextLong(150, 400));

        String price = product.currentPrice();
        return switch ((int) (id % 3)) {
            case 0 -> jsonLdPage(product, price);
            case 1 -> openGraphPage(product, price);
            default -> plainHtmlPage(product, price);
        };
    }

    /** Страница без цены: на ней видно, как сервис сообщает об ошибке разбора. */
    @GetMapping(value = "/no-price", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String noPrice() {
        return "<html><head><title>Снят с продажи</title></head><body><h1>Снят с продажи</h1></body></html>";
    }

    private static String jsonLdPage(DemoProduct p, String price) {
        return """
                <!doctype html><html lang="ru"><head><meta charset="utf-8"><title>%1$s</title>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"%1$s","description":"%2$s",
                 "datePublished":"%3$s","offers":{"@type":"Offer","price":"%4$s","priceCurrency":"%5$s"}}
                </script></head><body><h1>%1$s</h1></body></html>
                """.formatted(p.title(), p.description(), p.publishedOn(), price, p.currency());
    }

    private static String openGraphPage(DemoProduct p, String price) {
        return """
                <!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Магазин</title>
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="article:published_time" content="%3$sT09:00:00+03:00">
                <meta property="product:price:amount" content="%4$s">
                <meta property="product:price:currency" content="%5$s">
                </head><body><h1>%1$s</h1></body></html>
                """.formatted(p.title(), p.description(), p.publishedOn(), price, p.currency());
    }

    private static String plainHtmlPage(DemoProduct p, String price) {
        return """
                <!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Магазин</title></head>
                <body><article><h1>%1$s</h1><p>%2$s</p>
                <time datetime="%3$s">%3$s</time>
                <span class="product-price">%4$s %5$s</span></article></body></html>
                """.formatted(p.title(), p.description(), p.publishedOn(), price, p.currency());
    }

    private record DemoProduct(long id, String title, String description, String basePrice, String currency,
                               String publishedOn) {

        /** Базовая цена с отклонением до 5% в обе стороны. */
        String currentPrice() {
            double factor = ThreadLocalRandom.current().nextDouble(0.95, 1.05);
            return new BigDecimal(basePrice)
                    .multiply(BigDecimal.valueOf(factor))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        }
    }
}
