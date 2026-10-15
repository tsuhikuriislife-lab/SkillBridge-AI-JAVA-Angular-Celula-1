package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.AssessmentEvaluation;

import java.util.Map;
import java.util.UUID;

public interface EvaluateAssessmentUseCase {
    AssessmentEvaluation evaluate(UUID assessmentId, Map<String, Integer> answers);
}

