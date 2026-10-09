package com.riwi.skillbridge.application.port.in;

import java.util.UUID;

public interface ProcessBookingNotificationUseCase {
    boolean processBookingCreated(UUID bookingId);
}
