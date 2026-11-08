package com.example.priceparser.web;

import com.example.priceparser.dto.ParseRequest;
import com.example.priceparser.dto.ParseResult;
import com.example.priceparser.dto.ParserStatus;
import com.example.priceparser.dto.ResultLogEntry;
import com.example.priceparser.model.TrackedUrl;
import com.example.priceparser.service.ParsingService;
import com.example.priceparser.service.ResultFileLog;
import com.example.priceparser.service.TrackedUrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ParseController {

    private final TrackedUrlService trackedUrls;
    private final ParsingService parsingService;
    private final ResultFileLog fileLog;

    public ParseController(TrackedUrlService trackedUrls, ParsingService parsingService, ResultFileLog fileLog) {
        this.trackedUrls = trackedUrls;
        this.parsingService = parsingService;
        this.fileLog = fileLog;
    }

    /** Ставит ссылки на отслеживание, сразу обходит их параллельно и возвращает результат по каждой. */
    @PostMapping("/parse")
    public List<ParseResult> parse(@Valid @RequestBody ParseRequest request) {
        Set<String> accepted = new LinkedHashSet<>();
        List<ParseResult> results = new ArrayList<>();
        for (String raw : request.urls()) {
            Optional<String> url = TrackedUrlService.normalize(raw);
            if (url.isPresent()) {
                accepted.add(url.get());
            } else {
                results.add(ParseResult.rejected(raw));
            }
        }
        accepted.forEach(trackedUrls::register);
        results.addAll(parsingService.parseAll(accepted));
        return results;
    }

    /** Отслеживаемые ссылки с итогом последней проверки, включая ошибки. */
    @GetMapping("/urls")
    public List<TrackedUrl> urls() {
        return trackedUrls.findAll();
    }

    /** Последние записи файла результатов, сначала самые новые. */
    @GetMapping("/results-file")
    public List<ResultLogEntry> resultsFile(@RequestParam(defaultValue = "20") @Min(1) @Max(1000) int limit) {
        return fileLog.readLast(limit);
    }

    @GetMapping("/status")
    public ParserStatus status() {
        return parsingService.status();
    }
}
