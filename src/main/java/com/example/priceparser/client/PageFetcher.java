package com.example.priceparser.client;

import com.example.priceparser.config.ParserProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;

/** HTTP-клиент на WebClient. Вызывается из потоков пула парсинга, поэтому блокировка здесь допустима. */
@Component
public class PageFetcher {

    private final WebClient webClient;
    private final Duration blockTimeout;

    public PageFetcher(WebClient pageWebClient, ParserProperties properties) {
        this.webClient = pageWebClient;
        this.blockTimeout = properties.connectTimeout().plus(properties.readTimeout());
    }

    public FetchedPage fetch(String url) {
        ResponseEntity<byte[]> response;
        try {
            response = webClient.get()
                    .uri(URI.create(url))
                    .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML, MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class)
                    .block(blockTimeout);
        } catch (WebClientResponseException e) {
            throw new PageFetchException("HTTP " + e.getStatusCode().value());
        }

        if (response == null || response.getBody() == null || response.getBody().length == 0) {
            throw new PageFetchException("Сервер вернул пустой ответ");
        }

        HttpHeaders headers = response.getHeaders();
        MediaType contentType = headers.getContentType();
        if (contentType != null && !isHtml(contentType)) {
            throw new PageFetchException("Неподдерживаемый тип содержимого: " + contentType);
        }
        Charset charset = contentType == null ? null : contentType.getCharset();
        long lastModified = headers.getLastModified();

        return new FetchedPage(
                url,
                response.getBody(),
                charset == null ? null : charset.name(),
                lastModified > 0 ? Instant.ofEpochMilli(lastModified) : null);
    }

    private static boolean isHtml(MediaType type) {
        return type.isCompatibleWith(MediaType.TEXT_HTML)
                || type.isCompatibleWith(MediaType.APPLICATION_XHTML_XML)
                || type.isCompatibleWith(MediaType.TEXT_PLAIN);
    }
}
