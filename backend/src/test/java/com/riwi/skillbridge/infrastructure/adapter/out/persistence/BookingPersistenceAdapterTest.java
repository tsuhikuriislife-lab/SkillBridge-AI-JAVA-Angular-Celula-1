package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.application.model.BookingActivityFilter;
import com.riwi.skillbridge.application.model.BookingPage;
import com.riwi.skillbridge.application.model.BookingSort;
import com.riwi.skillbridge.application.model.BookingSummary;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.BookingListingProjection;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingPersistenceAdapterTest {
    @Test
    void shouldMapBookingListingProjectionToPageSummary() {
        JpaBookingRepository repository = mock(JpaBookingRepository.class);
        BookingPersistenceAdapter adapter = new BookingPersistenceAdapter(repository);
        UUID bookingId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-10-07T15:00:00Z");
        BookingListingProjection row = mock(BookingListingProjection.class);
        when(row.getId()).thenReturn(bookingId);
        when(row.getOfferingId()).thenReturn(offeringId);
        when(row.getOfferingTitle()).thenReturn("Java");
        when(row.getPrice()).thenReturn(BigDecimal.TEN);
        when(row.getScheduledAt()).thenReturn(scheduledAt);
        when(row.getStatus()).thenReturn(BookingStatus.CREATED.name());
        when(repository.findListingByCustomerId(
                eq(customerId), eq("ALL"), eq("DATE_DESC"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 10), 11));

        BookingPage result = adapter.findPageByCustomerId(
                customerId, 1, 10, BookingSort.DATE_DESC, BookingActivityFilter.ALL);

        assertEquals(new BookingPage(
                List.of(new BookingSummary(
                        bookingId, offeringId, "Java", BigDecimal.TEN, scheduledAt, BookingStatus.CREATED, true)),
                1,
                10,
                11,
                2
        ), result);
        verify(repository).findListingByCustomerId(
                eq(customerId), eq("ALL"), eq("DATE_DESC"), any(PageRequest.class));
    }
}
