package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingHistory(
    UUID id,
    UUID userId,
    UUID serviceId,
    EnrollmentStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt
)
{ }
