package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import java.util.List;

public record ClientAssessmentQuestionResponse(
        String id,
        String question,
        List<String> options
) {
}

