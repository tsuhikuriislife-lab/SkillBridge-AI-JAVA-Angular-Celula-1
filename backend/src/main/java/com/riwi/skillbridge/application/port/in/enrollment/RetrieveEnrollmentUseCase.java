package com.riwi.skillbridge.application.port.in.enrollment;

import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrieveEnrollmentUseCase {
    Optional<ServiceEnrollment> getEnrollment(UUID userId, UUID serviceId);
    List<ServiceEnrollment> getEnrollmentsByUser(UUID userId);

    PageResult<ServiceEnrollment> getEnrollmentsByUser(UUID userId, int page, int size);
    List<ServiceEnrollment> getEnrollmentsByService(UUID serviceId);
}
