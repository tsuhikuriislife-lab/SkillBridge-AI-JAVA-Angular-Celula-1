package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.WithdrawalResult;
import com.riwi.skillbridge.application.port.in.enrollment.*;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.ServiceEnrollmentService;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.EnrollmentOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.WithdrawalOut;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollUserUseCase enroll;
    private final RetrieveEnrollmentUseCase retrieve;
    private final WithdrawEnrollmentUseCase withdraw;
    private final ManageParticipantsUseCase participants;
    private final ServiceEnrollmentService enrollmentService;
    private final UserAccountPort users;

    public EnrollmentController(EnrollUserUseCase enroll, RetrieveEnrollmentUseCase retrieve,
                                WithdrawEnrollmentUseCase withdraw, ManageParticipantsUseCase participants,
                                ServiceEnrollmentService enrollmentService, UserAccountPort users) {
        this.enroll = enroll; this.retrieve = retrieve; this.withdraw = withdraw;
        this.participants = participants; this.enrollmentService = enrollmentService; this.users = users;
    }

    @PostMapping("/services/{serviceId}/enroll")
    public EnrollmentOut enroll(@AuthenticationPrincipal UserDetails user,
                                @PathVariable UUID serviceId) {
        UUID userId = currentUserId(user);
        return toOut(enroll.enroll(new ServiceEnrollment(userId, serviceId, null, 0, null, null)));
    }

    @GetMapping("/enrollments/me")
    public PageResult<EnrollmentOut> myEnrollments(@AuthenticationPrincipal UserDetails user,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        var result = retrieve.getEnrollmentsByUser(currentUserId(user),
                PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size));
        return new PageResult<>(result.content().stream().map(this::toOut).toList(),
                result.totalPages(), result.totalElements(), result.number());
    }

    /** Retiro voluntario: <24h → refund=true; después → false (cancelación sin reembolso). */
    @DeleteMapping("/enrollments/{serviceId}")
    public WithdrawalOut withdraw(@AuthenticationPrincipal UserDetails user,
                                  @PathVariable UUID serviceId) {
        WithdrawalResult result = withdraw.withdraw(currentUserId(user), serviceId);
        return new WithdrawalOut(toOut(result.enrollment()), result.refund());
    }

    @GetMapping("/provider/services/{serviceId}/participants")
    @PreAuthorize("hasRole('PROVIDER')")
    public PageResult<EnrollmentOut> listParticipants(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable UUID serviceId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        var result = participants.listParticipants(currentUserId(user), serviceId,
                PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size));
        return new PageResult<>(result.content().stream().map(this::toOut).toList(),
                result.totalPages(), result.totalElements(), result.number());
    }

    /** Regla §10: solo servicios GRATUITOS permiten remover participantes. */
    @DeleteMapping("/provider/services/{serviceId}/participants/{participantId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Void> removeParticipant(@AuthenticationPrincipal UserDetails user,
                                                  @PathVariable UUID serviceId,
                                                  @PathVariable UUID participantId) {
        participants.removeParticipant(currentUserId(user), serviceId, participantId);
        return ResponseEntity.noContent().build();
    }

    /** Disparo manual del auto-COMPLETED (respaldo del job programado). */
    @PostMapping("/admin/enrollments/complete-ended")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> completeEnded() {
        return Map.of("completed", enrollmentService.completeEndedEnrollments());
    }

    private UUID currentUserId(UserDetails user) {
        return users.findByEmail(user.getUsername())
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado")).id();
    }

    private EnrollmentOut toOut(ServiceEnrollment e) {
        return new EnrollmentOut(e.userId(), e.serviceId(), e.status(),
                e.remainingSessions(), e.startDate(), e.endDate());
    }
}
