package com.example.priceparser.service;

import com.example.priceparser.model.TrackedUrl;
import com.example.priceparser.repository.TrackedUrlRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TrackedUrlService {

    private final TrackedUrlRepository repository;

    public TrackedUrlService(TrackedUrlRepository repository) {
        this.repository = repository;
    }

    /** Проверяет ссылку и приводит её к ASCII-виду. Пустой результат означает, что ссылка некорректна. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String url = uri.toASCIIString();
            boolean valid = (scheme.equals("http") || scheme.equals("https"))
                    && uri.getHost() != null
                    && url.length() <= TrackedUrl.MAX_URL_LENGTH;
            return valid ? Optional.of(url) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    public void register(String url) {
        if (repository.existsByUrl(url)) {
            return;
        }
        try {
            repository.save(new TrackedUrl(url, Instant.now()));
        } catch (DataIntegrityViolationException ignored) {
            // Параллельный запрос успел добавить ту же ссылку: нужная запись уже есть
        }
    }

    public List<TrackedUrl> findAll() {
        return repository.findAll();
    }
}
