package com.example.priceparser.service;

import com.example.priceparser.dto.AnswerItem;
import com.example.priceparser.dto.AnswerPage;
import com.example.priceparser.dto.AnswerQuery;
import com.example.priceparser.model.PriceRecord;
import com.example.priceparser.repository.PriceRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Читает результаты из H2 и фильтрует, сортирует и постранично отдаёт их через parallelStream. */
@Service
public class AnswerService {

    public static final int MAX_PAGE_SIZE = 100;

    private final PriceRecordRepository repository;

    public AnswerService(PriceRecordRepository repository) {
        this.repository = repository;
    }

    public AnswerPage find(AnswerQuery query) {
        if (query.page() < 0 || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw badRequest("page должен быть >= 0, size от 1 до " + MAX_PAGE_SIZE);
        }
        Comparator<PriceRecord> comparator = comparator(query.sortBy(), query.direction());

        Collection<PriceRecord> source = query.latestOnly() ? latestPerUrl() : repository.findAll();

        // sorted на параллельном стриме сохраняет порядок сортировки при сборке в список
        List<AnswerItem> sorted = source.parallelStream()
                .filter(filter(query))
                .sorted(comparator)
                .map(AnswerItem::from)
                .toList();

        long offset = (long) query.page() * query.size();
        List<AnswerItem> content = offset >= sorted.size()
                ? List.of()
                : sorted.subList((int) offset, (int) Math.min(offset + query.size(), sorted.size()));
        int totalPages = (int) Math.ceil((double) sorted.size() / query.size());

        return new AnswerPage(content, query.page(), query.size(), sorted.size(), totalPages);
    }

    /** Последняя запись по каждой ссылке. */
    private Collection<PriceRecord> latestPerUrl() {
        ConcurrentMap<String, PriceRecord> latest = repository.findAll().parallelStream()
                .collect(Collectors.toConcurrentMap(
                        PriceRecord::getUrl,
                        Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(PriceRecord::getId))));
        return latest.values();
    }

    private static Predicate<PriceRecord> filter(AnswerQuery query) {
        String search = query.q() == null || query.q().isBlank() ? null : query.q().strip().toLowerCase(Locale.ROOT);
        return record -> (search == null || contains(record.getTitle(), search) || contains(record.getDescription(), search))
                && (query.url() == null || query.url().equals(record.getUrl()))
                && (query.currency() == null || query.currency().equalsIgnoreCase(record.getCurrency()))
                && (query.minPrice() == null || record.getPrice().compareTo(query.minPrice()) >= 0)
                && (query.maxPrice() == null || record.getPrice().compareTo(query.maxPrice()) <= 0);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private static Comparator<PriceRecord> comparator(String sortBy, String direction) {
        boolean descending = switch (direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw badRequest("direction должен быть asc или desc");
        };
        Comparator<PriceRecord> byField = switch (sortBy) {
            case "price" -> by(PriceRecord::getPrice, descending);
            case "title" -> by(record -> record.getTitle().toLowerCase(Locale.ROOT), descending);
            case "publishedAt" -> by(PriceRecord::getPublishedAt, descending);
            case "parsedAt" -> by(PriceRecord::getParsedAt, descending);
            default -> throw badRequest("sortBy должен быть одним из: price, title, publishedAt, parsedAt");
        };
        return byField.thenComparing(PriceRecord::getId);
    }

    /** Записи без значения всегда идут в конце, при любом направлении сортировки. */
    private static <K extends Comparable<? super K>> Comparator<PriceRecord> by(
            Function<PriceRecord, K> key, boolean descending) {
        Comparator<K> order = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(key, Comparator.nullsLast(order));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
