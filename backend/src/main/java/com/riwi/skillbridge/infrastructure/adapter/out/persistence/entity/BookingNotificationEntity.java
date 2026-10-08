package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_notifications")
public class BookingNotificationEntity {
    @Id
    @Column(name = "notification_id")
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    protected BookingNotificationEntity() {}

    public BookingNotificationEntity(
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
    ) {
        this.id = id;
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.type = type;
        this.status = status;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
        this.expiresAt = expiresAt;
        this.errorCode = errorCode;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getEventType() { return eventType; }
    public NotificationType getType() { return type; }
    public NotificationStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getErrorCode() { return errorCode; }
}
