package com.riwi.skillbridge.application.model;

import com.riwi.skillbridge.domain.model.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingSummary(
        UUID id,
        UUID offeringId,
        String offeringTitle,
        BigDecimal price,
        Instant scheduledAt,
        BookingStatus status,
        boolean active
) {}