package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetBookingsUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final CreateBookingUseCase createUseCase;
    private final GetBookingsUseCase consultUseCase;

    public BookingController(CreateBookingUseCase createUseCase, GetBookingsUseCase consultUseCase) {
        this.createUseCase = createUseCase;
        this.consultUseCase = consultUseCase;

    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication) {
        return createUseCase.create(request.offeringId(), request.scheduledAt(), authentication.getName());
    }

}
