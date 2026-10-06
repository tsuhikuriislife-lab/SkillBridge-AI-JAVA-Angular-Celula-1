package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.model.BookingPage;
import com.riwi.skillbridge.application.model.BookingActivityFilter;
import com.riwi.skillbridge.application.model.BookingSort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    @Test
    void shouldListOnlyBookingsForAuthenticatedCustomerAndRequestedPage() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        UUID userId = UUID.randomUUID();
        BookingPage expected = new BookingPage(java.util.List.of(), 1, 25, 30, 2);
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findPageByCustomerId(userId, 1, 25, BookingSort.TITLE_ASC, BookingActivityFilter.ACTIVE)).thenReturn(expected);

        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertEquals(expected, service.list("user@example.com", 1, 25, BookingSort.TITLE_ASC, BookingActivityFilter.ACTIVE));
        verify(bookings).findPageByCustomerId(userId, 1, 25, BookingSort.TITLE_ASC, BookingActivityFilter.ACTIVE);
    }

    @Test
    void shouldRejectInvalidPagination() {
        BookingService service = new BookingService(
                mock(BookingRepositoryPort.class),
                mock(OfferingRepositoryPort.class),
                mock(UserAccountPort.class),
                mock(BookingEventPublisherPort.class)
        );

        assertThrows(BusinessRuleException.class, () -> service.list(
            "user@example.com", -1, 10, BookingSort.DATE_DESC, BookingActivityFilter.ALL));
        assertThrows(BusinessRuleException.class, () -> service.list(
            "user@example.com", 0, 0, BookingSort.DATE_DESC, BookingActivityFilter.ALL));
        assertThrows(BusinessRuleException.class, () -> service.list(
            "user@example.com", 0, 101, BookingSort.DATE_DESC, BookingActivityFilter.ALL));
        }

        @Test
        void shouldRejectPastBookingWithoutCallingDependencies() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertThrows(BusinessRuleException.class, () -> service.create(
            UUID.randomUUID(), Instant.now().minusSeconds(1), "user@example.com"));

        verifyNoInteractions(bookings, offerings, users, publisher);
        }

        @Test
        void shouldRejectInactiveOfferingWithoutSavingOrPublishing() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, false)));
        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertThrows(BusinessRuleException.class, () -> service.create(
            offeringId, Instant.now().plusSeconds(3600), "user@example.com"));

        verifyNoInteractions(bookings, publisher, users);
        }

        @Test
        void shouldRejectUnknownOffering() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.empty());
        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertThrows(DomainNotFoundException.class, () -> service.create(
            offeringId, Instant.now().plusSeconds(3600), "user@example.com"));

        verifyNoInteractions(bookings, publisher, users);
        }

        @Test
        void shouldRejectUnknownCustomerWithoutSavingOrPublishing() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true)));
        when(users.findIdByEmail("missing@example.com")).thenReturn(Optional.empty());
        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertThrows(DomainNotFoundException.class, () -> service.create(
            offeringId, Instant.now().plusSeconds(3600), "missing@example.com"));

        verifyNoInteractions(bookings, publisher);
    }

    @Test
    void shouldPersistAndPublishBookingCreated() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Offering offering = new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, offerings, users, publisher);
        Booking result = service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        assertEquals(BookingStatus.CREATED, result.status());
        verify(bookings).save(any(Booking.class));
        verify(publisher).bookingCreated(result);
    }
}
