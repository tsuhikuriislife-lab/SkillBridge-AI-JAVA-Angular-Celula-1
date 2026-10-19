package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.MeetingParticipantStatus;
import com.riwi.skillbridge.domain.enums.MeetingRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetingParticipantOut(
    UUID id,
    UUID userId,
    MeetingRole role,
    MeetingParticipantStatus status,
    OffsetDateTime joinedAt,
    OffsetDateTime leftAt,
    String denialReason
) {}
