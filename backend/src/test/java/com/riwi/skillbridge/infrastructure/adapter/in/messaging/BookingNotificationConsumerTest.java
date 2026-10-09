package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.ProcessBookingNotificationUseCase;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.BookingCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BookingNotificationConsumerTest {
    @Test
    void shouldCountProcessedAndDuplicateEventsSeparately() {
        ProcessBookingNotificationUseCase useCase = mock(ProcessBookingNotificationUseCase.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingNotificationConsumer consumer = new BookingNotificationConsumer(useCase, registry);
        UUID bookingId = UUID.randomUUID();
        BookingCreatedEvent event = new BookingCreatedEvent(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now());
        when(useCase.processBookingCreated(bookingId)).thenReturn(true, false);

        consumer.onBookingCreated(event);
        consumer.onBookingCreated(event);

        assertEquals(1, registry.counter("skillbridge.notifications.processed").count());
        assertEquals(1, registry.counter("skillbridge.notifications.duplicate").count());
        assertEquals(2, registry.timer("skillbridge.notifications.processing").count());
    }

    @Test
    void shouldCountFailureAndRethrowForRabbitRetryAndDeadLetterHandling() {
        ProcessBookingNotificationUseCase useCase = mock(ProcessBookingNotificationUseCase.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookingNotificationConsumer consumer = new BookingNotificationConsumer(useCase, registry);
        BookingCreatedEvent event = new BookingCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now());
        when(useCase.processBookingCreated(event.bookingId())).thenThrow(new IllegalStateException("sensitive details"));

        assertThrows(IllegalStateException.class, () -> consumer.onBookingCreated(event));

        assertEquals(1, registry.counter("skillbridge.notifications.failed_attempts").count());
    }
}
