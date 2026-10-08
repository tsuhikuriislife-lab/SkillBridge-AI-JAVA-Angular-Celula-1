package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record Offering(
        UUID id,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active,
        UUID providerId,
        LocalTime startTime,
        LocalTime endTime,
        String endDay,
        String photoUrl
) {}
