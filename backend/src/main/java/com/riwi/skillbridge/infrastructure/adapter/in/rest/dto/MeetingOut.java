package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetingOut(
    UUID id,
    UUID serviceId,
    LocalDate meetingDate,
    LocalTime startTime,
    int durationMinutes,
    MeetingStatus status,
    OffsetDateTime scheduledStart,
    OffsetDateTime actualStart,
    OffsetDateTime actualEnd,
    OffsetDateTime maxEnd,
    boolean extensionUsed,
    String cancellationReason,
    UUID cancelledBy,
    OffsetDateTime createdAt
) {}
