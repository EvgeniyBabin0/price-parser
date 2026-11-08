package com.example.priceparser.web;

import com.example.priceparser.dto.AnswerPage;
import com.example.priceparser.dto.AnswerQuery;
import com.example.priceparser.service.AnswerService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;

@Controller
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/answer";
    }

    @GetMapping("/answer")
    @ResponseBody
    public AnswerPage answer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "parsedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "true") boolean latestOnly) {
        return answerService.find(new AnswerQuery(
                page, size, sortBy, direction, q, url, currency, minPrice, maxPrice, latestOnly));
    }
}
