package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceEnrollmentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceEnrollmentId;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaServiceEnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ServiceEnrollmentPersistenceAdapter implements ServiceEnrollmentPort {

    private final JpaServiceEnrollmentRepository repository;

    public ServiceEnrollmentPersistenceAdapter(JpaServiceEnrollmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public ServiceEnrollment save(ServiceEnrollment e) {
        return toDomain(repository.save(new ServiceEnrollmentEntity(e.userId(), e.serviceId(),
                e.status(), e.remainingSessions(), e.startDate(), e.endDate())));
    }

    @Override
    public Optional<ServiceEnrollment> findByUserIdAndServiceId(UUID userId, UUID serviceId) {
        return repository.findById(new ServiceEnrollmentId(userId, serviceId)).map(this::toDomain);
    }

    @Override
    public List<ServiceEnrollment> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<ServiceEnrollment> findByUserId(UUID userId, int page, int size) {
        Page<ServiceEnrollmentEntity> result = repository.findByUserId(userId, PageRequest.of(page, size));
        return toPage(result);
    }

    @Override
    public List<ServiceEnrollment> findByServiceId(UUID serviceId) {
        return repository.findByServiceId(serviceId).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<ServiceEnrollment> findByServiceId(UUID serviceId, int page, int size) {
        Page<ServiceEnrollmentEntity> result = repository.findByServiceId(serviceId, PageRequest.of(page, size));
        return toPage(result);
    }

    @Override
    public List<ServiceEnrollment> findByStatus(EnrollmentStatus status) {
        return repository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByServiceIdAndStatus(UUID serviceId, EnrollmentStatus status) {
        return repository.countByServiceIdAndStatus(serviceId, status);
    }

    @Override
    public void delete(UUID userId, UUID serviceId) {
        repository.deleteById(new ServiceEnrollmentId(userId, serviceId));
    }

    private PageResult<ServiceEnrollment> toPage(Page<ServiceEnrollmentEntity> result) {
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalPages(), result.getTotalElements(), result.getNumber());
    }

    private ServiceEnrollment toDomain(ServiceEnrollmentEntity e) {
        return new ServiceEnrollment(e.getUserId(), e.getServiceId(), e.getStatus(),
                e.getRemainingSessions(), e.getStartDate(), e.getEndDate());
    }
}
