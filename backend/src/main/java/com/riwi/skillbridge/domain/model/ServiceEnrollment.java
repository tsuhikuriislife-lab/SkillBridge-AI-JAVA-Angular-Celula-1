package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ServiceEnrollment(
    UUID userId,
    UUID serviceId,
    EnrollmentStatus status,
    int remainingSessions,
    LocalDate startDate,
    LocalDate endDate
) {
}
