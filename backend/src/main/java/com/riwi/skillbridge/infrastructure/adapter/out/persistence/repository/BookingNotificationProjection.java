package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface BookingNotificationProjection {
    UUID getNotificationId();
    UUID getBookingId();
    String getEventType();
    String getNotificationType();
    String getStatus();
    String getTitle();
    String getMessage();
    Instant getCreatedAt();
    Instant getProcessedAt();
}
