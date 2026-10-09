package com.riwi.skillbridge.domain.model;

import java.util.List;

public record AssessmentQuestion(
        String id,
        String question,
        List<String> options,
        int correctOptionIndex,
        String explanation
) {
    public AssessmentQuestion {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("El id de la pregunta es requerido");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("La pregunta es requerida");
        if (options == null || options.isEmpty()) throw new IllegalArgumentException("Las opciones son requeridas");
        if (correctOptionIndex < 0 || correctOptionIndex >= options.size()) {
            throw new IllegalArgumentException("El índice de opción correcta está fuera de rango");
        }
    }
}

