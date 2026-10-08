package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.TechnicalAssessment;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentSessionCachePort {
    void save(TechnicalAssessment assessment);
    Optional<TechnicalAssessment> findById(UUID assessmentId);
}

