package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.bookinghistory.RetrieveBookingHistoryUseCase;
import com.riwi.skillbridge.application.port.in.offering.RetrieveOfferingUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.BookingHistory;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Ciclo de vida completo de una inscripción (quién/qué/cuándo). Actúa como el nuevo sistema de notificaciones. */
@RestController
@RequestMapping("/api")
public class BookingHistoryController {

    private final RetrieveBookingHistoryUseCase history;
    private final RetrieveOfferingUseCase offeringUseCase;
    private final UserAccountPort userAccountPort;

    public BookingHistoryController(RetrieveBookingHistoryUseCase history,
                                    RetrieveOfferingUseCase offeringUseCase,
                                    UserAccountPort userAccountPort) {
        this.history = history;
        this.offeringUseCase = offeringUseCase;
        this.userAccountPort = userAccountPort;
    }

    @GetMapping("/services/{serviceId}/users/{userId}/history")
    public List<BookingHistory> getHistory(@PathVariable UUID serviceId, @PathVariable UUID userId) {
        return history.getHistory(userId, serviceId);
    }

    @GetMapping("/history/me")
    public PageResult<HistoryNotificationOut> getMyHistory(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = currentUserId(user);
        var result = history.getMyHistory(userId, PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size));
        
        List<HistoryNotificationOut> mapped = result.content().stream().map(h -> {
            String title = offeringUseCase.getOfferingById(h.serviceId()).map(Offering::name).orElse("Servicio desconocido");
            String statusMapped = mapStatus(h.status().name());
            String message = "El estado de tu inscripción es ahora: " + h.status().name();
            return new HistoryNotificationOut(
                    h.id().toString(), "HISTORY", message, statusMapped,
                    h.createdAt().toString(), null, title, h.serviceId().toString()
            );
        }).toList();

        return new PageResult<>(mapped, result.totalPages(), result.totalElements(), result.number());
    }

    private String mapStatus(String historyStatus) {
        if ("ACTIVE".equals(historyStatus)) return "PROCESSED";
        if ("CANCELLED".equals(historyStatus)) return "FAILED";
        return "PROCESSED"; // COMPLETED -> PROCESSED
    }

    private UUID currentUserId(UserDetails user) {
        return userAccountPort.findByEmail(user.getUsername())
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado")).id();
    }

    public record HistoryNotificationOut(
            String id,
            String type,
            String message,
            String status,
            String createdAt,
            String readAt,
            String title,
            String bookingId
    ) {}
}
