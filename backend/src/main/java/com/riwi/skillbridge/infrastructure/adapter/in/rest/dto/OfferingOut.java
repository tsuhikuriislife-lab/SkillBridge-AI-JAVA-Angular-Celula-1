package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.ServiceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OfferingOut(
    UUID id, String name, UUID categoryId, BigDecimal price,
    String detail, String shortDescription, String learningObjectives, String prerequisites,
    Integer capacity, String code, ServiceStatus status, UUID createdBy,
    OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
