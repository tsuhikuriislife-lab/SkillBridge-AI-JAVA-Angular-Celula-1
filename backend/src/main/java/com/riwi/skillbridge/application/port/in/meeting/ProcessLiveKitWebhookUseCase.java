package com.riwi.skillbridge.application.port.in.meeting;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Puerto de entrada para el procesamiento de eventos Webhook de LiveKit.
 * Sincroniza en tiempo real el ciclo de vida de las reuniones (inicio/fin)
 * y la asistencia de los participantes (ingreso/salida) (HU-29.3).
 */
public interface ProcessLiveKitWebhookUseCase {

    /**
     * Notificación de inicio de sala (room_started).
     * Transiciona la reunión a EN_CURSO y fija actualStart.
     */
    void handleRoomStarted(UUID meetingId, OffsetDateTime timestamp);

    /**
     * Notificación de finalización de sala (room_finished).
     * Transiciona la reunión a FINALIZADA, fija actualEnd y cierra asistencias activas.
     */
    void handleRoomFinished(UUID meetingId, OffsetDateTime timestamp);

    /**
     * Notificación de ingreso de participante (participant_joined).
     * Actualiza el estado a EN_SESION con joinedAt.
     */
    void handleParticipantJoined(UUID meetingId, UUID userId, OffsetDateTime timestamp);

    /**
     * Notificación de salida de participante (participant_left).
     * Actualiza el estado a SALIDA_REGISTRADA con leftAt.
     */
    void handleParticipantLeft(UUID meetingId, UUID userId, OffsetDateTime timestamp);
}

