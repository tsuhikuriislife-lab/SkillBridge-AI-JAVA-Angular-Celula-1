package com.riwi.skillbridge.domain.model;

import java.util.List;

public record AssessmentQuestionResult(
        String questionId,
        String question,
        List<String> options,
        Integer selectedOptionIndex,
        int correctOptionIndex,
        boolean isCorrect,
        String explanation
) {
}

