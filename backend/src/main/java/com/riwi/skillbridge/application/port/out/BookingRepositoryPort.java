package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.application.model.BookingPage;
import com.riwi.skillbridge.application.model.BookingActivityFilter;
import com.riwi.skillbridge.application.model.BookingSort;

import java.util.UUID;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    BookingPage findPageByCustomerId(UUID customerId, int page, int size, BookingSort sort, BookingActivityFilter activity);
}
