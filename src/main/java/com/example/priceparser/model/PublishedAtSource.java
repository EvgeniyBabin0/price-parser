package com.example.priceparser.model;

/** Откуда взята дата публикации: из разметки страницы или из HTTP-заголовка Last-Modified. */
public enum PublishedAtSource {
    PAGE,
    HTTP_LAST_MODIFIED
}
