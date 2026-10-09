package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record AssessmentSubmissionRequest(
        @NotNull(message = "Las respuestas son requeridas")
        Map<String, Integer> answers
) {
}

