package com.example.priceparser.dto;

import com.example.priceparser.model.ParseStatus;

/** Итог обработки одной ссылки. Поле product заполнено только при статусе SUCCESS. */
public record ParseResult(String url, ParseStatus status, String error, AnswerItem product) {

    public static ParseResult success(String url, AnswerItem product) {
        return new ParseResult(url, ParseStatus.SUCCESS, null, product);
    }

    public static ParseResult failed(String url, String error) {
        return new ParseResult(url, ParseStatus.FAILED, error, null);
    }

    public static ParseResult skipped(String url) {
        return new ParseResult(url, ParseStatus.SKIPPED_IN_PROGRESS, "Ссылка уже обрабатывается другим потоком", null);
    }

    public static ParseResult timedOut(String url) {
        return new ParseResult(url, ParseStatus.TIMEOUT, "Задача не завершилась вовремя", null);
    }

    public static ParseResult rejected(String url) {
        return new ParseResult(url, ParseStatus.REJECTED, "Нужна корректная ссылка http или https", null);
    }
}
