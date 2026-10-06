package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingPersistenceAdapterTest {
    @Test
    void shouldMapAllBookingEntitiesToDomainBookings() {
        JpaBookingRepository repository = mock(JpaBookingRepository.class);
        BookingPersistenceAdapter adapter = new BookingPersistenceAdapter(repository);
        UUID bookingId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-10-07T15:00:00Z");
        BookingEntity entity = new BookingEntity(
                bookingId, offeringId, customerId, scheduledAt, BookingStatus.CREATED, Instant.now());
        when(repository.findAll()).thenReturn(List.of(entity));

        List<Booking> result = adapter.findAll();

        assertEquals(List.of(new Booking(
                bookingId, offeringId, customerId, scheduledAt, BookingStatus.CREATED)), result);
        verify(repository).findAll();
    }
}
