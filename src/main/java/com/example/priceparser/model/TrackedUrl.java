package com.example.priceparser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Ссылка на товар, которую планировщик обходит по расписанию. */
@Entity
@Table(name = "tracked_url")
public class TrackedUrl {

    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_URL_LENGTH)
    private String url;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ParseStatus lastStatus;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    private Instant lastCheckedAt;

    protected TrackedUrl() {
    }

    public TrackedUrl(String url, Instant createdAt) {
        this.url = url;
        this.createdAt = createdAt;
    }

    public void recordCheck(ParseStatus status, String error, Instant checkedAt) {
        this.lastStatus = status;
        this.lastError = error;
        this.lastCheckedAt = checkedAt;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ParseStatus getLastStatus() {
        return lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
}
