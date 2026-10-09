package com.riwi.skillbridge.application.model;

import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        UUID bookingId,
        String eventType,
        NotificationType type,
        NotificationStatus status,
        String title,
        String message,
        Instant createdAt,
        Instant processedAt
) {}
