package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.NotificationPage;
import com.riwi.skillbridge.application.port.in.ListMyNotificationsUseCase;
import com.riwi.skillbridge.application.port.in.ProcessBookingNotificationUseCase;
import com.riwi.skillbridge.application.port.in.PurgeExpiredNotificationsUseCase;
import com.riwi.skillbridge.application.port.out.NotificationRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.BookingNotification;
import com.riwi.skillbridge.domain.model.NotificationStatus;
import com.riwi.skillbridge.domain.model.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService implements
        ProcessBookingNotificationUseCase,
        ListMyNotificationsUseCase,
        PurgeExpiredNotificationsUseCase {
    private static final String BOOKING_CREATED_EVENT = "BOOKING_CREATED";
    private static final String BOOKING_CREATED_TITLE = "Reserva creada";
    private static final String BOOKING_CREATED_MESSAGE = "Tu reserva fue registrada correctamente.";

    private final NotificationRepositoryPort notificationRepository;
    private final UserAccountPort userAccountPort;
    private final Duration retention;

    public NotificationService(
            NotificationRepositoryPort notificationRepository,
            UserAccountPort userAccountPort,
            @Value("${app.notifications.retention-days:90}") int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("El período de retención debe ser de al menos un día");
        }
        this.notificationRepository = notificationRepository;
        this.userAccountPort = userAccountPort;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Override
    @Transactional
    public boolean processBookingCreated(UUID bookingId) {
        Instant now = Instant.now();
        BookingNotification notification = new BookingNotification(
                UUID.randomUUID(),
                bookingId,
                BOOKING_CREATED_EVENT,
                NotificationType.IN_APP,
                NotificationStatus.PROCESSED,
                BOOKING_CREATED_TITLE,
                BOOKING_CREATED_MESSAGE,
                now,
                now,
                now.plus(retention),
                null
        );
        return notificationRepository.saveIfAbsent(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage list(String customerEmail, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("La página no puede ser negativa y el tamaño debe estar entre 1 y 100");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        return notificationRepository.findPageByCustomerId(customerId, page, size, Instant.now());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return notificationRepository.deleteExpiredBefore(Instant.now());
    }
}
