package com.riwi.skillbridge.application.port.in.enrollment;

import com.riwi.skillbridge.domain.model.ServiceEnrollment;

public interface EnrollUserUseCase {
    ServiceEnrollment enroll(ServiceEnrollment enrollment);
}
