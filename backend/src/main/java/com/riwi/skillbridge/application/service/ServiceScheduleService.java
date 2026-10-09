package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.serviceschedule.ManageServiceScheduleUseCase;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceScheduleService implements ManageServiceScheduleUseCase {

    private final ServiceSchedulePort serviceSchedulePort;
    private final OfferingPort offeringPort;

    public ServiceScheduleService(ServiceSchedulePort serviceSchedulePort, OfferingPort offeringPort) {
        this.serviceSchedulePort = serviceSchedulePort;
        this.offeringPort = offeringPort;
    }

    @Override
    public ServiceSchedule createSchedule(ServiceSchedule s) {
        validate(s);
        ServiceSchedule toSave = new ServiceSchedule(
                s.id() != null ? s.id() : UUID.randomUUID(),
                s.serviceId(), s.startDay(), s.sessionDuration(),
                s.frequency(), s.numberOfSessions(), s.startDate(),
                s.startTime(), s.endTime()
        );
        return serviceSchedulePort.save(toSave);
    }

    @Override
    public Optional<ServiceSchedule> getScheduleById(UUID id) {
        return serviceSchedulePort.findById(id);
    }

    @Override
    public List<ServiceSchedule> getSchedulesByService(UUID serviceId) {
        return serviceSchedulePort.findByServiceId(serviceId);
    }

    @Override
    public Optional<ServiceSchedule> updateSchedule(UUID id, ServiceSchedule s) {
        ServiceSchedule existing = serviceSchedulePort.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Horario no encontrado con id: " + id));
        ServiceSchedule merged = new ServiceSchedule(
                id, existing.serviceId(),                    // 🔒 el servicio no cambia
                s.startDay() != null ? s.startDay() : existing.startDay(),
                s.sessionDuration() > 0 ? s.sessionDuration() : existing.sessionDuration(),
                s.frequency() != null ? s.frequency() : existing.frequency(),
                s.numberOfSessions() > 0 ? s.numberOfSessions() : existing.numberOfSessions(),
                s.startDate() != null ? s.startDate() : existing.startDate(),
                s.startTime() != null ? s.startTime() : existing.startTime(),
                s.endTime() != null ? s.endTime() : existing.endTime());
        validate(merged);
        return Optional.of(serviceSchedulePort.save(merged));
    }

    @Override
    public boolean deleteSchedule(UUID id) {
        if (serviceSchedulePort.findById(id).isEmpty()) return false;
        serviceSchedulePort.deleteById(id);
        return true;
    }

    // Centralizado y testeable (plan §6): la fecha fin NUNCA viene del cliente
    public static LocalDate calculateEndDate(ServiceSchedule s) {
        int sessions = Math.max(s.numberOfSessions(), 1);
        return switch (s.frequency()) {
            case WEEKLY -> s.startDate().plusWeeks(sessions - 1);
            case BIWEEKLY -> s.startDate().plusWeeks((sessions - 1) * 2L);
            case MONTHLY -> s.startDate().plusMonths(sessions - 1);
        };
    }

    private void validate(ServiceSchedule s) {
        Offering service = offeringPort.findById(s.serviceId())
                .orElseThrow(() -> new BusinessRuleException("El servicio no existe: " + s.serviceId()));
        if (s.numberOfSessions() <= 0)
            throw new BusinessRuleException("La cantidad de sesiones debe ser mayor que 0");
        if (s.sessionDuration() <= 0)
            throw new BusinessRuleException("La duración de la sesión debe ser mayor que 0");
        if (s.startDate() == null)
            throw new BusinessRuleException("La fecha de inicio es obligatoria");
        // 🛡️ REGLA 48 HORAS: mínimo 2 días después de hoy
        if (!s.startDate().isAfter(LocalDate.now().plusDays(1)))
            throw new BusinessRuleException(
                "El servicio debe comenzar con al menos 48 horas de anticipación (fecha mínima: "
                + LocalDate.now().plusDays(2) + ")");
        if (s.startDay() != null && s.startDate().getDayOfWeek() != s.startDay())
            throw new BusinessRuleException("La fecha de inicio no coincide con el día de inicio indicado");
    }
}
