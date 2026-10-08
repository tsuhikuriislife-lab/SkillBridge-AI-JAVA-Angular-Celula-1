package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TechnicalAssessment(
        UUID id,
        UUID offeringId,
        String offeringTitle,
        List<AssessmentQuestion> questions,
        Instant createdAt
) {
    public TechnicalAssessment {
        if (id == null) id = UUID.randomUUID();
        if (offeringId == null) throw new IllegalArgumentException("El ID del servicio es requerido");
        if (questions == null || questions.isEmpty()) throw new IllegalArgumentException("Las preguntas son requeridas");
        if (createdAt == null) createdAt = Instant.now();
    }
}

