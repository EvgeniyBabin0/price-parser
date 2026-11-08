package com.example.priceparser.dto;

import java.util.List;

public record AnswerPage(List<AnswerItem> content, int page, int size, long totalElements, int totalPages) {
}
