package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.BookingPage;
import com.riwi.skillbridge.application.model.BookingActivityFilter;
import com.riwi.skillbridge.application.model.BookingSort;

public interface ListMyBookingsUseCase {
    BookingPage list(String customerEmail, int page, int size, BookingSort sort, BookingActivityFilter activity);
}