package com.riwi.skillbridge.domain.model;

import java.util.List;
import java.util.UUID;

public record AssessmentEvaluation(
        UUID assessmentId,
        UUID offeringId,
        String offeringTitle,
        int score,
        int totalQuestions,
        boolean passed,
        String feedback,
        String recommendation,
        List<AssessmentQuestionResult> questionsFeedback
) {
}

