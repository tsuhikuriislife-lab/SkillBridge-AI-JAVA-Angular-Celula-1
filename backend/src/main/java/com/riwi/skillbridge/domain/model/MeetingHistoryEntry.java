package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.MeetingEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro auditable de un acontecimiento de una reunión. No almacena tokens
 * JWT ni credenciales sensibles de LiveKit.
 */
public record MeetingHistoryEntry(
    UUID id,
    UUID meetingId,
    UUID userId,
    MeetingEventType eventType,
    String result,
    String reason,
    String metadata,
    OffsetDateTime createdAt
) {}
