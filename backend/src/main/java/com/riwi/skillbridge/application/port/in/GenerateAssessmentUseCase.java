package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.TechnicalAssessment;

import java.util.UUID;

public interface GenerateAssessmentUseCase {
    TechnicalAssessment generate(UUID offeringId);
}

