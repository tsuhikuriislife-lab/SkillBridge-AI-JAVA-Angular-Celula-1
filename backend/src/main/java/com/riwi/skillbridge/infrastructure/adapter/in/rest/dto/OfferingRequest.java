package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.application.model.OfferingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferingRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal price,
        @Size(max = 500) String shortDescription,
        String detail,
        String learningObjectives,
        String prerequisites,
        @Positive Integer capacity
) {
    public OfferingCommand toCommand() {
        return new OfferingCommand(name, categoryId, price, shortDescription,
                detail, learningObjectives, prerequisites, capacity);
    }
}