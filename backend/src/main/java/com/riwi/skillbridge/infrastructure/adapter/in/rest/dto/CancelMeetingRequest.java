package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Motivo de cancelación de una reunión (Regla 5: queda registrado con el responsable). */
public record CancelMeetingRequest(
    @NotBlank(message = "El motivo de cancelación es obligatorio")
    String reason
) {}
