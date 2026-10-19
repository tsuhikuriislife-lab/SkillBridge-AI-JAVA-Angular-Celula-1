package com.riwi.skillbridge.application.port.in.meeting;

import com.riwi.skillbridge.application.model.MeetingAccess;

import java.util.UUID;

/**
 * Autorización de acceso a una reunión (Regla 2): verifica que el usuario tenga
 * una inscripción activa en el servicio o sea el proveedor propietario, que la
 * reunión esté en curso y dentro de su límite de tiempo, y genera un token de
 * LiveKit exclusivo para esa identidad.
 */
public interface JoinMeetingUseCase {

    MeetingAccess join(UUID userId, UUID meetingId);
}
