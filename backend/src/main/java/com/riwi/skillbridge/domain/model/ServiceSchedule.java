package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ServiceSchedule(
    UUID id,
    UUID serviceId,
    DayOfWeek startDay,
    int sessionDuration,
    Frequency frequency,
    int numberOfSessions,
    LocalDate startDate,
    LocalTime startTime,
    LocalTime endTime
) {
}
