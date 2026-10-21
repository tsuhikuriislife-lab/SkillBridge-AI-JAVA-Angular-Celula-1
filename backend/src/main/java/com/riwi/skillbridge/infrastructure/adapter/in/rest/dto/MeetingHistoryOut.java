package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.MeetingEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetingHistoryOut(
    UUID id,
    UUID userId,
    MeetingEventType eventType,
    String result,
    String reason,
    String metadata,
    OffsetDateTime createdAt
) {}
