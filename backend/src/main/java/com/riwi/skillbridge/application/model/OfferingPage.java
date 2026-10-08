package com.riwi.skillbridge.application.model;

import java.util.List;

import com.riwi.skillbridge.domain.model.Offering;

public record OfferingPage(
    List<Offering> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
