package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Frequency;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceScheduleRequest(
    @NotNull DayOfWeek startDay,
    @NotNull @Min(1) Integer sessionDuration,
    @NotNull Frequency frequency,
    @NotNull @Min(1) Integer numberOfSessions,
    @NotNull LocalDate startDate
) {}
