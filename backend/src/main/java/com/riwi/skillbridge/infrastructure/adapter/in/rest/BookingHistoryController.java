package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.bookinghistory.RetrieveBookingHistoryUseCase;
import com.riwi.skillbridge.domain.model.BookingHistory;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Ciclo de vida completo de una inscripción (quién/qué/cuándo). */
@RestController
@RequestMapping("/api")
public class BookingHistoryController {

    private final RetrieveBookingHistoryUseCase history;

    public BookingHistoryController(RetrieveBookingHistoryUseCase history) {
        this.history = history;
    }

    @GetMapping("/services/{serviceId}/users/{userId}/history")
    public List<BookingHistory> getHistory(@PathVariable UUID serviceId, @PathVariable UUID userId) {
        return history.getHistory(userId, serviceId);
    }
}
