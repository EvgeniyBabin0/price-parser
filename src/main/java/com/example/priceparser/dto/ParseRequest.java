package com.example.priceparser.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ParseRequest(
        @NotEmpty(message = "Передайте хотя бы одну ссылку")
        @Size(max = 100, message = "За один запрос можно передать не более 100 ссылок")
        List<String> urls
) {
}
