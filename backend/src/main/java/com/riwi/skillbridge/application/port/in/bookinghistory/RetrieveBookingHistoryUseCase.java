package com.riwi.skillbridge.application.port.in.bookinghistory;

import com.riwi.skillbridge.domain.model.BookingHistory;

import java.util.List;
import java.util.UUID;

public interface RetrieveBookingHistoryUseCase {
    List<BookingHistory> getHistory(UUID userId, UUID serviceId);
}
