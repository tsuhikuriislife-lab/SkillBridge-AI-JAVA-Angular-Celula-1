package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.model.NotificationPage;
import com.riwi.skillbridge.domain.model.BookingNotification;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepositoryPort {
    boolean saveIfAbsent(BookingNotification notification);
    NotificationPage findPageByCustomerId(UUID customerId, int page, int size, Instant now);
    int deleteExpiredBefore(Instant now);
}
