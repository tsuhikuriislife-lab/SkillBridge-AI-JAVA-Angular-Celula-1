package com.riwi.skillbridge.application.port.in.bookinghistory;

import com.riwi.skillbridge.domain.model.BookingHistory;

public interface RecordBookingsHistoryUseCase {
    BookingHistory record(BookingHistory bookingHistory);
}
