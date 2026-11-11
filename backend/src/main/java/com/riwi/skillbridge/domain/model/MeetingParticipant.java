package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.MeetingParticipantStatus;
import com.riwi.skillbridge.domain.enums.MeetingRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetingParticipant(
    UUID id,
    UUID meetingId,
    UUID userId,
    MeetingRole role,
    MeetingParticipantStatus status,
    OffsetDateTime joinedAt,
    OffsetDateTime leftAt,
    String denialReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
