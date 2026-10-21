package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.MeetingRole;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Credenciales de acceso a una reunión. El token es de un solo uso conceptual y expira con la reunión. */
public record MeetingTokenOut(
    UUID meetingId,
    String livekitUrl,
    String token,
    MeetingRole role,
    OffsetDateTime tokenExpiresAt
) {}
