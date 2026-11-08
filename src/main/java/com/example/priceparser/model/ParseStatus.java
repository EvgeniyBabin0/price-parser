package com.example.priceparser.model;

public enum ParseStatus {
    /** Страница разобрана, запись сохранена. */
    SUCCESS,
    /** Страницу не удалось скачать или разобрать. */
    FAILED,
    /** Эта ссылка уже обрабатывается другим потоком. */
    SKIPPED_IN_PROGRESS,
    /** Задача не уложилась в parser.task-timeout, результат может появиться позже. */
    TIMEOUT,
    /** Ссылка некорректна и не была принята. */
    REJECTED
}
