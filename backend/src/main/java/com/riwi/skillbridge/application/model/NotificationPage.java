package com.riwi.skillbridge.application.model;

import java.util.List;

public record NotificationPage(
        List<NotificationSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
