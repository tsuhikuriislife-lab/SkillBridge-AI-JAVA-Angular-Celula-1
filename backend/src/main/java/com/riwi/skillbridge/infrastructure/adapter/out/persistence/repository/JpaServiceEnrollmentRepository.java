package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceEnrollmentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceEnrollmentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaServiceEnrollmentRepository
        extends JpaRepository<ServiceEnrollmentEntity, ServiceEnrollmentId> {

    List<ServiceEnrollmentEntity> findByUserId(UUID userId);
    Page<ServiceEnrollmentEntity> findByUserId(UUID userId, Pageable pageable);
    List<ServiceEnrollmentEntity> findByServiceId(UUID serviceId);
    Page<ServiceEnrollmentEntity> findByServiceId(UUID serviceId, Pageable pageable);
    List<ServiceEnrollmentEntity> findByStatus(EnrollmentStatus status);
    long countByServiceIdAndStatus(UUID serviceId, EnrollmentStatus status);
}
