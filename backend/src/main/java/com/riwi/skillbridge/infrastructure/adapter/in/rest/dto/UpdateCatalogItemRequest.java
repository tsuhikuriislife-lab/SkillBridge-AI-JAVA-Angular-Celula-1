package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Status;
import jakarta.validation.constraints.NotBlank;

public record UpdateCatalogItemRequest(
    @NotBlank(message = "El nombre no puede estar en blanco")
    String name,
    String detail,
    Status status
) {}
