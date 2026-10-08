package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.OfferingStatus;
import jakarta.validation.constraints.NotNull;

public record OfferingStatusRequest(@NotNull OfferingStatus status) {
}