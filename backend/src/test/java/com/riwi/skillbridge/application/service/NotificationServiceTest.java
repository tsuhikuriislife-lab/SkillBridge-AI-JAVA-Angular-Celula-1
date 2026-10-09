package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.NotificationPage;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.model.BookingNotification;
import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepositoryPort notifications = mock(NotificationRepositoryPort.class);
    private final UserAccountPort users = mock(UserAccountPort.class);
    private final NotificationService service = new NotificationService(notifications, users, 90);

    @Test
    void shouldPersistBookingCreatedNotificationAsProcessed() {
        UUID bookingId = UUID.randomUUID();
        when(notifications.saveIfAbsent(any(BookingNotification.class))).thenReturn(true);

        assertTrue(service.processBookingCreated(bookingId));

        var captor = org.mockito.ArgumentCaptor.forClass(BookingNotification.class);
        verify(notifications).saveIfAbsent(captor.capture());
        BookingNotification saved = captor.getValue();
        assertEquals(bookingId, saved.bookingId());
        assertEquals("BOOKING_CREATED", saved.eventType());
        assertEquals(NotificationType.IN_APP, saved.type());
        assertEquals(NotificationStatus.PROCESSED, saved.status());
        assertEquals(saved.createdAt(), saved.processedAt());
        assertTrue(saved.expiresAt().isAfter(saved.createdAt()));
    }

    @Test
    void shouldReportDuplicateWithoutCreatingAnotherNotification() {
        when(notifications.saveIfAbsent(any(BookingNotification.class))).thenReturn(false);

        assertFalse(service.processBookingCreated(UUID.randomUUID()));

        verify(notifications).saveIfAbsent(any(BookingNotification.class));
    }

    @Test
    void shouldListOnlyAuthenticatedCustomersNotifications() {
        UUID customerId = UUID.randomUUID();
        NotificationPage expected = new NotificationPage(List.of(), 0, 20, 0, 0);
        when(users.findIdByEmail("customer@example.com")).thenReturn(Optional.of(customerId));
        when(notifications.findPageByCustomerId(eq(customerId), eq(0), eq(20), any(Instant.class))).thenReturn(expected);

        assertEquals(expected, service.list("customer@example.com", 0, 20));

        verify(notifications).findPageByCustomerId(eq(customerId), eq(0), eq(20), any(Instant.class));
    }
}
