package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceScheduleEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaServiceScheduleRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ServiceSchedulePersistenceAdapter implements ServiceSchedulePort {

    private final JpaServiceScheduleRepository repository;

    public ServiceSchedulePersistenceAdapter(JpaServiceScheduleRepository repository) {
        this.repository = repository;
    }

    @Override
    public ServiceSchedule save(ServiceSchedule s) {
        return toDomain(repository.save(new ServiceScheduleEntity(s.id(), s.serviceId(), s.startDay(),
                s.sessionDuration(), s.frequency(), s.numberOfSessions(), s.startDate())));
    }

    @Override
    public Optional<ServiceSchedule> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ServiceSchedule> findByServiceId(UUID serviceId) {
        return repository.findByServiceId(serviceId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) { repository.deleteById(id); }

    private ServiceSchedule toDomain(ServiceScheduleEntity e) {
        return new ServiceSchedule(e.getId(), e.getServiceId(), e.getStartDay(),
                e.getSessionDuration(), e.getFrequency(), e.getNumberOfSessions(), e.getStartDate());
    }
}
