package com.riwi.skillbridge.application.model;

import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.domain.model.Meeting;

import java.time.OffsetDateTime;

/**
 * Credenciales de acceso a una reunión en curso: la reunión, la URL del servidor
 * LiveKit y el token de acceso exclusivo de la identidad autorizada.
 * El token nunca se persiste en base de datos.
 */
public record MeetingAccess(
    Meeting meeting,
    String livekitUrl,
    String token,
    MeetingRole role,
    OffsetDateTime tokenExpiresAt
) {}
