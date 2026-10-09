package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.BookingNotification;
import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingNotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingNotificationPersistenceAdapterTest {
    @Test
    void shouldTranslateInsertConflictIntoIdempotentResult() {
        JpaBookingNotificationRepository repository = mock(JpaBookingNotificationRepository.class);
        BookingNotificationPersistenceAdapter adapter = new BookingNotificationPersistenceAdapter(repository);
        Instant now = Instant.parse("2026-10-07T15:00:00Z");
        BookingNotification notification = new BookingNotification(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BOOKING_CREATED",
                NotificationType.IN_APP,
                NotificationStatus.PROCESSED,
                "Reserva creada",
                "Tu reserva fue registrada correctamente.",
                now,
                now,
                now.plusSeconds(90 * 24 * 60 * 60L),
                null
        );
        when(repository.insertIfAbsent(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                any(Instant.class),
                any(Instant.class),
                isNull()
        )).thenReturn(1, 0);

        assertTrue(adapter.saveIfAbsent(notification));
        assertFalse(adapter.saveIfAbsent(notification));

        verify(repository, org.mockito.Mockito.times(2)).insertIfAbsent(
                notification.id(),
                notification.bookingId(),
                notification.eventType(),
                notification.type().name(),
                notification.status().name(),
                notification.title(),
                notification.message(),
                notification.createdAt(),
                notification.processedAt(),
                notification.expiresAt(),
                null
        );
    }
}
