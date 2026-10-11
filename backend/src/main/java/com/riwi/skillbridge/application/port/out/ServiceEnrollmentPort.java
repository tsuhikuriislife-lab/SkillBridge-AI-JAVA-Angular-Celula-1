package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceEnrollmentPort {

    ServiceEnrollment save(ServiceEnrollment enrollment);

    Optional<ServiceEnrollment> findByUserIdAndServiceId(UUID userId, UUID serviceId);

    List<ServiceEnrollment> findByUserId(UUID userId);

    PageResult<ServiceEnrollment> findByUserId(UUID userId, int page, int size);

    List<ServiceEnrollment> findByServiceId(UUID serviceId);

    PageResult<ServiceEnrollment> findByServiceId(UUID serviceId, int page, int size);

    List<ServiceEnrollment> findByStatus(EnrollmentStatus status);

    long countByServiceIdAndStatus(UUID serviceId, EnrollmentStatus status);

    void delete(UUID userId, UUID serviceId);
}
