package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record BookingNotification(
        UUID id,
        UUID bookingId,
        String eventType,
        NotificationType type,
        NotificationStatus status,
        String title,
        String message,
        Instant createdAt,
        Instant processedAt,
        Instant expiresAt,
        String errorCode
) {}
