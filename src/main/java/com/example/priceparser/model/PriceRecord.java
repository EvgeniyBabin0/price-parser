package com.example.priceparser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Результат одного успешного обхода страницы. Записи накапливаются и образуют историю цены. */
@Entity
@Table(name = "price_record", indexes = @Index(name = "idx_price_record_url", columnList = "url"))
public class PriceRecord {

    public static final int MAX_TITLE_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = TrackedUrl.MAX_URL_LENGTH)
    private String url;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(length = 3)
    private String currency;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PublishedAtSource publishedAtSource;

    @Column(nullable = false)
    private Instant parsedAt;

    @Column(nullable = false, length = 64)
    private String threadName;

    protected PriceRecord() {
    }

    public PriceRecord(String url, String title, String description, BigDecimal price, String currency,
                       Instant publishedAt, PublishedAtSource publishedAtSource, Instant parsedAt, String threadName) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.publishedAt = publishedAt;
        this.publishedAtSource = publishedAtSource;
        this.parsedAt = parsedAt;
        this.threadName = threadName;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public PublishedAtSource getPublishedAtSource() {
        return publishedAtSource;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public String getThreadName() {
        return threadName;
    }
}
