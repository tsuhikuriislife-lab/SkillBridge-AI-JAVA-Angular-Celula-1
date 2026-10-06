package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;

public interface GetBookingsUseCase {
    List<Booking> findAll();
}
