package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import java.util.List;
import java.util.UUID;

public record ClientAssessmentResponse(
        UUID assessmentId,
        UUID offeringId,
        String offeringTitle,
        List<ClientAssessmentQuestionResponse> questions
) {
}

