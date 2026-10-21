package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Meeting(
    UUID id,
    UUID serviceId,
    UUID scheduleId,
    LocalDate meetingDate,
    LocalTime startTime,
    int durationMinutes,
    MeetingStatus status,
    String livekitRoom,
    boolean roomCreated,
    OffsetDateTime scheduledStart,
    OffsetDateTime actualStart,
    OffsetDateTime actualEnd,
    OffsetDateTime maxEnd,
    int extensionMinutes,
    boolean extensionUsed,
    String cancellationReason,
    UUID cancelledBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
