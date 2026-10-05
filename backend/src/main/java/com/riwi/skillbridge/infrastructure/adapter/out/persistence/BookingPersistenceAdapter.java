package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.model.BookingPage;
import com.riwi.skillbridge.application.model.BookingSummary;
import com.riwi.skillbridge.application.model.BookingActivityFilter;
import com.riwi.skillbridge.application.model.BookingSort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {
    private final JpaBookingRepository repository;

    public BookingPersistenceAdapter(JpaBookingRepository repository) { this.repository = repository; }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = new BookingEntity(
                booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), booking.status(), Instant.now());
        BookingEntity saved = repository.save(entity);
        return new Booking(saved.getId(), saved.getOfferingId(), saved.getCustomerId(), saved.getScheduledAt(), saved.getStatus());
    }

    @Override
    public BookingPage findPageByCustomerId(
            java.util.UUID customerId,
            int page,
            int size,
            BookingSort sort,
            BookingActivityFilter activity
    ) {
        var result = repository.findListingByCustomerId(
                customerId,
                activity.name(),
                sort.name(),
                PageRequest.of(page, size, Sort.unsorted())
        );
        var content = result.getContent().stream()
                .map(row -> {
                    BookingStatus status = BookingStatus.valueOf(row.getStatus());
                    boolean active = status == BookingStatus.CREATED || status == BookingStatus.CONFIRMED;
                    return new BookingSummary(
                            row.getId(),
                            row.getOfferingId(),
                            row.getOfferingTitle(),
                            row.getPrice(),
                            row.getScheduledAt(),
                            status,
                            active
                    );
                })
                .toList();
        return new BookingPage(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
