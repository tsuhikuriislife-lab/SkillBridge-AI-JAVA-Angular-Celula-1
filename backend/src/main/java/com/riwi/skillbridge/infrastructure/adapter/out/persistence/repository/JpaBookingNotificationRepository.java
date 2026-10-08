package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingNotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface JpaBookingNotificationRepository extends JpaRepository<BookingNotificationEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO booking_notifications (
                notification_id, booking_id, event_type, notification_type, status,
                title, message, created_at, processed_at, expires_at, error_code
            ) VALUES (
                :id, :bookingId, :eventType, :notificationType, :status,
                :title, :message, :createdAt, :processedAt, :expiresAt, :errorCode
            )
            ON CONFLICT (booking_id, event_type, notification_type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("bookingId") UUID bookingId,
            @Param("eventType") String eventType,
            @Param("notificationType") String notificationType,
            @Param("status") String status,
            @Param("title") String title,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt,
            @Param("processedAt") Instant processedAt,
            @Param("expiresAt") Instant expiresAt,
            @Param("errorCode") String errorCode
    );

    @Query(value = """
            SELECT sh.notification_id AS "notificationId",
                   sh.booking_id AS "bookingId",
                   sh.event_type AS "eventType",
                   sh.notification_type AS "notificationType",
                   sh.status AS "status",
                   sh.title AS "title",
                   sh.message AS "message",
                   sh.created_at AS "createdAt",
                   sh.processed_at AS "processedAt"
            FROM booking_notifications sh
            JOIN bookings b ON b.id = sh.booking_id
            WHERE b.customer_id = :customerId
              AND sh.expires_at > :now
            ORDER BY sh.created_at DESC, sh.notification_id DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM booking_notifications sh
                    JOIN bookings b ON b.id = sh.booking_id
                    WHERE b.customer_id = :customerId
                      AND sh.expires_at > :now
                    """,
            nativeQuery = true)
    Page<BookingNotificationProjection> findForCustomer(
            @Param("customerId") UUID customerId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query(value = "DELETE FROM booking_notifications WHERE expires_at <= :now", nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
