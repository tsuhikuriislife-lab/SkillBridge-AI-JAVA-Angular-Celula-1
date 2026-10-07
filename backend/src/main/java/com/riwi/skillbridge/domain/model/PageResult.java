package com.riwi.skillbridge.domain.model;

import java.util.List;

public record PageResult<T>(
    List<T> content,
    int totalPages,
    long totalElements,
    int number
) {}
