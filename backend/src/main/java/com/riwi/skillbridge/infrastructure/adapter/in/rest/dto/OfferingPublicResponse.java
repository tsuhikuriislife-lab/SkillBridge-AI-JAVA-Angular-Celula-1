package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Offering;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferingPublicResponse(
        UUID id,
        String code,
        String name,
        UUID categoryId,
        BigDecimal price,
        String shortDescription,
        String detail,
        String learningObjectives,
        String prerequisites,
        Integer capacity
) {
    public static OfferingPublicResponse from(Offering offering) {
        return new OfferingPublicResponse(
                offering.id(), offering.code(), offering.name(), offering.categoryId(), offering.price(),
                offering.shortDescription(), offering.detail(), offering.learningObjectives(),
                offering.prerequisites(), offering.capacity());
    }
}