package com.example.priceparser.client;

import java.time.Instant;

/** Скачанная страница. charset и lastModified равны null, если сервер их не указал. */
public record FetchedPage(String url, byte[] body, String charset, Instant lastModified) {
}
