package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.BookingHistory;

import java.util.List;
import java.util.UUID;

public interface BookingHistoryPort {

    BookingHistory save(BookingHistory bookingHistory);

    List<BookingHistory> findByUserIdAndServiceId(UUID userId, UUID serviceId);

}
