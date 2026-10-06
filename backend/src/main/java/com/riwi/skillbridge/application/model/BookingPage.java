package com.riwi.skillbridge.application.model;

import java.util.List;

public record BookingPage(
        List<BookingSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}