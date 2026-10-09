package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.ServiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Offering(
    UUID id,
    String name,
    UUID categoryId,        // ← antes era String category; ahora FK real a catalog_items
    BigDecimal price,
    String detail,
    String shortDescription,
    String learningObjectives,
    String prerequisites,
    Integer capacity,
    String code,
    ServiceStatus status,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
