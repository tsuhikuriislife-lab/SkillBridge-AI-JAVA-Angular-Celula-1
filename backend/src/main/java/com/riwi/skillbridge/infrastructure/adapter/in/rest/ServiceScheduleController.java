package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.offering.RetrieveOfferingUseCase;
import com.riwi.skillbridge.application.port.in.serviceschedule.ManageServiceScheduleUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ServiceScheduleOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ServiceScheduleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/provider/services/{serviceId}/schedule")
@PreAuthorize("hasRole('PROVIDER')")
public class ServiceScheduleController {

    private final ManageServiceScheduleUseCase schedules;
    private final RetrieveOfferingUseCase offerings;
    private final UserAccountPort users;

    public ServiceScheduleController(ManageServiceScheduleUseCase schedules,
                                     RetrieveOfferingUseCase offerings, UserAccountPort users) {
        this.schedules = schedules; this.offerings = offerings; this.users = users;
    }

    @PostMapping
    public ServiceScheduleOut create(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable UUID serviceId,
                                     @Valid @RequestBody ServiceScheduleRequest req) {
        assertOwnership(user, serviceId);
        return toOut(schedules.createSchedule(new ServiceSchedule(null, serviceId, req.startDay(),
                req.sessionDuration(), req.frequency(), req.numberOfSessions(), req.startDate(), req.startTime(), req.startTime().plusMinutes(req.sessionDuration()))));
    }

    @GetMapping
    public List<ServiceScheduleOut> getByService(@AuthenticationPrincipal UserDetails user,
                                                 @PathVariable UUID serviceId) {
        assertOwnership(user, serviceId);
        return schedules.getSchedulesByService(serviceId).stream().map(this::toOut).toList();
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ServiceScheduleOut> update(@AuthenticationPrincipal UserDetails user,
                                                     @PathVariable UUID serviceId,
                                                     @PathVariable UUID scheduleId,
                                                     @Valid @RequestBody ServiceScheduleRequest req) {
        assertOwnership(user, serviceId);
        return schedules.updateSchedule(scheduleId, new ServiceSchedule(null, serviceId, req.startDay(),
                        req.sessionDuration(), req.frequency(), req.numberOfSessions(), req.startDate(), req.startTime(), req.startTime().plusMinutes(req.sessionDuration())))
                .map(s -> ResponseEntity.ok(toOut(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails user,
                                       @PathVariable UUID serviceId,
                                       @PathVariable UUID scheduleId) {
        assertOwnership(user, serviceId);
        return schedules.deleteSchedule(scheduleId)
                ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private void assertOwnership(UserDetails user, UUID serviceId) {
        UUID providerId = users.findByEmail(user.getUsername())
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado")).id();
        Offering o = offerings.getOfferingById(serviceId)
                .orElseThrow(() -> new BusinessRuleException("Servicio no encontrado"));
        if (!o.createdBy().equals(providerId))
            throw new BusinessRuleException("No puedes administrar servicios de otro proveedor");
    }

    private ServiceScheduleOut toOut(ServiceSchedule s) {
        return new ServiceScheduleOut(s.id(), s.serviceId(), s.startDay(), s.sessionDuration(),
                s.frequency(), s.numberOfSessions(), s.startDate(), s.startTime(), s.endTime());
    }
}
