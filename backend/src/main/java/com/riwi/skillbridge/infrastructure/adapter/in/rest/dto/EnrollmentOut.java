package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentOut(
    UUID userId, UUID serviceId, EnrollmentStatus status,
    int remainingSessions, LocalDate startDate, LocalDate endDate
) {}
