package com.riwi.skillbridge.application.port.in.enrollment;

import java.util.UUID;

public interface CancelEnrollmentUseCase {
    boolean cancelEnrollment(UUID userId, UUID serviceId);
}
