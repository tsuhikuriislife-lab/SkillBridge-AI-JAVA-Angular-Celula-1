package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceUpsertRequest(
    @NotBlank(message = "El nombre es obligatorio") String name,
    @NotNull(message = "La categoría es obligatoria") UUID categoryId,
    @NotNull @DecimalMin(value = "0.0", message = "El precio no puede ser negativo (0 = gratuito)")
    BigDecimal price,
    String detail,
    String shortDescription,
    String learningObjectives,
    String prerequisites,
    @NotNull @Min(value = 1, message = "La capacidad debe ser mayor que cero") Integer capacity,
    @NotBlank(message = "El código es obligatorio") String code
) {}
