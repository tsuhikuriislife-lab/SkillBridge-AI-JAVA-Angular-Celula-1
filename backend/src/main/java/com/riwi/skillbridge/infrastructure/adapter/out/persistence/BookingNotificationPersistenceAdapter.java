package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.model.NotificationPage;
import com.riwi.skillbridge.application.model.NotificationSummary;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.domain.model.BookingNotification;
import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BookingNotificationPersistenceAdapter implements NotificationRepositoryPort {
    private final JpaBookingNotificationRepository repository;

    public BookingNotificationPersistenceAdapter(JpaBookingNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean saveIfAbsent(BookingNotification notification) {
        return repository.insertIfAbsent(
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
                notification.errorCode()
        ) == 1;
    }

    @Override
    public NotificationPage findPageByCustomerId(UUID customerId, int page, int size, Instant now) {
        var result = repository.findForCustomer(customerId, now, PageRequest.of(page, size, Sort.unsorted()));
        var content = result.getContent().stream()
                .map(row -> new NotificationSummary(
                        row.getNotificationId(),
                        row.getBookingId(),
                        row.getEventType(),
                        NotificationType.valueOf(row.getNotificationType()),
                        NotificationStatus.valueOf(row.getStatus()),
                        row.getTitle(),
                        row.getMessage(),
                        row.getCreatedAt(),
                        row.getProcessedAt()
                ))
                .toList();
        return new NotificationPage(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public int deleteExpiredBefore(Instant now) {
        return repository.deleteExpired(now);
    }
}
