package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.WithdrawalResult;
import com.riwi.skillbridge.application.port.in.enrollment.CancelEnrollmentUseCase;
import com.riwi.skillbridge.application.port.in.enrollment.EnrollUserUseCase;
import com.riwi.skillbridge.application.port.in.enrollment.ManageParticipantsUseCase;
import com.riwi.skillbridge.application.port.in.enrollment.RetrieveEnrollmentUseCase;
import com.riwi.skillbridge.application.port.in.enrollment.WithdrawEnrollmentUseCase;
import com.riwi.skillbridge.application.port.out.BookingHistoryPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.BookingHistory;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceEnrollmentService implements EnrollUserUseCase, RetrieveEnrollmentUseCase,
        CancelEnrollmentUseCase, WithdrawEnrollmentUseCase, ManageParticipantsUseCase {

    private final ServiceEnrollmentPort enrollmentPort;
    private final OfferingPort offeringPort;
    private final ServiceSchedulePort schedulePort;
    private final UserAccountPort userAccountPort;
    private final BookingHistoryPort historyPort;

    public ServiceEnrollmentService(ServiceEnrollmentPort enrollmentPort, OfferingPort offeringPort,
                                    ServiceSchedulePort schedulePort, UserAccountPort userAccountPort,
                                    BookingHistoryPort historyPort) {
        this.enrollmentPort = enrollmentPort;
        this.offeringPort = offeringPort;
        this.schedulePort = schedulePort;
        this.userAccountPort = userAccountPort;
        this.historyPort = historyPort;
    }

    @Override
    public ServiceEnrollment enroll(ServiceEnrollment request) {
        userAccountPort.findById(request.userId())
                .orElseThrow(() -> new BusinessRuleException("El usuario no existe"));
        Offering service = offeringPort.findById(request.serviceId())
                .orElseThrow(() -> new BusinessRuleException("El servicio no existe"));
        if (service.status() != ServiceStatus.ACTIVE)
            throw new BusinessRuleException("El servicio no está disponible");

        ServiceSchedule schedule = schedulePort.findByServiceId(service.id()).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("El servicio no tiene horario definido"));
        if (!schedule.startDate().isAfter(LocalDate.now()))
            throw new BusinessRuleException("El servicio ya comenzó, no admite inscripciones");

        enrollmentPort.findByUserIdAndServiceId(request.userId(), service.id())
                .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                .ifPresent(e -> { throw new BusinessRuleException("Ya estás inscrito en este servicio"); });

        long active = enrollmentPort.countByServiceIdAndStatus(service.id(), EnrollmentStatus.ACTIVE);
        if (service.capacity() != null && active >= service.capacity())
            throw new BusinessRuleException("No hay cupos disponibles en este servicio");

        ServiceEnrollment enrollment = new ServiceEnrollment(
                request.userId(), service.id(), EnrollmentStatus.ACTIVE,
                schedule.numberOfSessions(), schedule.startDate(),
                ServiceScheduleService.calculateEndDate(schedule));
        ServiceEnrollment saved = enrollmentPort.save(enrollment);
        recordHistory(saved, EnrollmentStatus.ACTIVE);
        return saved;
    }

    @Override
    public Optional<ServiceEnrollment> getEnrollment(UUID userId, UUID serviceId) {
        return enrollmentPort.findByUserIdAndServiceId(userId, serviceId);
    }

    @Override
    public List<ServiceEnrollment> getEnrollmentsByUser(UUID userId) {
        return enrollmentPort.findByUserId(userId);
    }

    @Override
    public PageResult<ServiceEnrollment> getEnrollmentsByUser(UUID userId, int page, int size) {
        return enrollmentPort.findByUserId(userId, page, size);
    }

    @Override
    public List<ServiceEnrollment> getEnrollmentsByService(UUID serviceId) {
        return enrollmentPort.findByServiceId(serviceId);
    }

    @Override
    public boolean cancelEnrollment(UUID userId, UUID serviceId) {
        return doCancel(userId, serviceId, false).enrollment() != null;
    }

    @Override
    public WithdrawalResult withdraw(UUID userId, UUID serviceId) {
        // Plan §9: dentro de 24h desde la inscripción → reembolso; después → sin reembolso
        OffsetDateTime enrolledAt = historyPort.findByUserIdAndServiceId(userId, serviceId).stream()
                .min(Comparator.comparing(BookingHistory::createdAt))
                .map(BookingHistory::createdAt)
                .orElse(OffsetDateTime.now());
        boolean within24h = ChronoUnit.HOURS.between(enrolledAt, OffsetDateTime.now()) < 24;
        return doCancel(userId, serviceId, within24h);
    }

    @Override
    public PageResult<ServiceEnrollment> listParticipants(UUID providerId, UUID serviceId, int page, int size) {
        assertOwnsService(providerId, serviceId);
        return enrollmentPort.findByServiceId(serviceId, page, size);
    }

    @Override
    public void removeParticipant(UUID providerId, UUID serviceId, UUID participantId) {
        Offering service = assertOwnsService(providerId, serviceId);
        // 🛡️ Plan §10: solo servicios GRATUITOS permiten remover participantes
        if (service.price().signum() > 0)
            throw new BusinessRuleException("Solo se pueden remover participantes de servicios gratuitos");
        doCancel(participantId, serviceId, false);
    }

    // Job auto-COMPLETED: marca inscripciones cuyo endDate ya pasó. Idempotente.
    public int completeEndedEnrollments() {
        int completed = 0;
        for (ServiceEnrollment e : enrollmentPort.findByStatus(EnrollmentStatus.ACTIVE)) {
            if (e.endDate() != null && !e.endDate().isAfter(LocalDate.now())) {
                ServiceEnrollment done = new ServiceEnrollment(
                        e.userId(), e.serviceId(), EnrollmentStatus.COMPLETED,
                        e.remainingSessions(), e.startDate(), e.endDate());
                enrollmentPort.save(done);
                recordHistory(done, EnrollmentStatus.COMPLETED);
                completed++;
            }
        }
        return completed;
    }

    // ---- internos ----

    private Offering assertOwnsService(UUID providerId, UUID serviceId) {
        Offering service = offeringPort.findById(serviceId)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + serviceId));
        if (!service.createdBy().equals(providerId))
            throw new BusinessRuleException("No puedes administrar servicios de otro proveedor");
        return service;
    }

    private WithdrawalResult doCancel(UUID userId, UUID serviceId, boolean refund) {
        ServiceEnrollment existing = enrollmentPort.findByUserIdAndServiceId(userId, serviceId)
                .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> new DomainNotFoundException("No tienes una inscripción activa en este servicio"));
        ServiceEnrollment cancelled = new ServiceEnrollment(
                existing.userId(), existing.serviceId(), EnrollmentStatus.CANCELLED,
                existing.remainingSessions(), existing.startDate(), existing.endDate());
        ServiceEnrollment saved = enrollmentPort.save(cancelled);
        recordHistory(saved, EnrollmentStatus.CANCELLED);
        return new WithdrawalResult(saved, refund);
    }

    private void recordHistory(ServiceEnrollment e, EnrollmentStatus status) {
        historyPort.save(new BookingHistory(UUID.randomUUID(), e.userId(), e.serviceId(),
                status, OffsetDateTime.now(), null));
    }
}
