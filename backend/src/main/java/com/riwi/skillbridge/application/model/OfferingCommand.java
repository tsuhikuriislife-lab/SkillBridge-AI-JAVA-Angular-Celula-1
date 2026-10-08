package com.riwi.skillbridge.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferingCommand(
        String name,
        UUID categoryId,
        BigDecimal price,
        String shortDescription,
        String detail,
        String learningObjectives,
        String prerequisites,
        Integer capacity
) {}