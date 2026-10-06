package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    List<Booking> findAll();
}
