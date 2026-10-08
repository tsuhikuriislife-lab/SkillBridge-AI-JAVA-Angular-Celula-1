package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Frequency;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceScheduleOut(
    UUID id, UUID serviceId, DayOfWeek startDay, int sessionDuration,
    Frequency frequency, int numberOfSessions, LocalDate startDate
) {}
