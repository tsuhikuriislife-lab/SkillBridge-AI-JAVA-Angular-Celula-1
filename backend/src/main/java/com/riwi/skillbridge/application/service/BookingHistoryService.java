package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.bookinghistory.RecordBookingsHistoryUseCase;
import com.riwi.skillbridge.application.port.in.bookinghistory.RetrieveBookingHistoryUseCase;
import com.riwi.skillbridge.application.port.out.BookingHistoryPort;
import com.riwi.skillbridge.domain.model.BookingHistory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookingHistoryService implements RecordBookingsHistoryUseCase, RetrieveBookingHistoryUseCase {

    private final BookingHistoryPort bookingHistoryPort;

    public BookingHistoryService(BookingHistoryPort bookingHistoryPort) {
        this.bookingHistoryPort = bookingHistoryPort;
    }

    @Override
    public BookingHistory record(BookingHistory bookingHistory) {
        return bookingHistoryPort.save(bookingHistory);
    }

    @Override
    public List<BookingHistory> getHistory(UUID userId, UUID serviceId) {
        return bookingHistoryPort.findByUserIdAndServiceId(userId, serviceId);
    }
}
