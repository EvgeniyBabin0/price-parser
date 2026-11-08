package com.example.priceparser.service;

import com.example.priceparser.dto.AnswerItem;
import com.example.priceparser.model.ParseStatus;
import com.example.priceparser.model.PriceRecord;
import com.example.priceparser.model.TrackedUrl;
import com.example.priceparser.parser.ExtractedProduct;
import com.example.priceparser.repository.PriceRecordRepository;
import com.example.priceparser.repository.TrackedUrlRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Сохраняет результат отдельной короткой транзакцией: соединение с БД не занято, пока страница скачивается. */
@Component
public class ParseResultWriter {

    private final PriceRecordRepository priceRecords;
    private final TrackedUrlRepository trackedUrls;

    public ParseResultWriter(PriceRecordRepository priceRecords, TrackedUrlRepository trackedUrls) {
        this.priceRecords = priceRecords;
        this.trackedUrls = trackedUrls;
    }

    @Transactional
    public AnswerItem saveSuccess(String url, ExtractedProduct product, String threadName) {
        Instant now = Instant.now();
        PriceRecord saved = priceRecords.save(new PriceRecord(
                url,
                truncate(product.title(), PriceRecord.MAX_TITLE_LENGTH),
                truncate(product.description(), PriceRecord.MAX_DESCRIPTION_LENGTH),
                product.price(),
                product.currency(),
                product.publishedAt(),
                product.publishedAtSource(),
                now,
                threadName));
        trackedUrls.findByUrl(url).ifPresent(tracked -> tracked.recordCheck(ParseStatus.SUCCESS, null, now));
        return AnswerItem.from(saved);
    }

    @Transactional
    public void saveFailure(String url, String error) {
        trackedUrls.findByUrl(url).ifPresent(tracked -> tracked.recordCheck(
                ParseStatus.FAILED, truncate(error, TrackedUrl.MAX_ERROR_LENGTH), Instant.now()));
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
