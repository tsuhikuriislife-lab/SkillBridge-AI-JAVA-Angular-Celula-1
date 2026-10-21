package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.domain.enums.Frequency;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MeetingScheduleResolverTest {

    private ServiceSchedule schedule(Frequency frequency, LocalDate startDate, int sessions) {
        return new ServiceSchedule(UUID.randomUUID(), UUID.randomUUID(), startDate.getDayOfWeek(),
            60, frequency, sessions, startDate, LocalTime.of(15, 0), LocalTime.of(16, 0));
    }

    @Test
    void weekly_returnsSameDayWhenStartDateIsInFuture() {
        // martes 2026-10-20, sesiones semanales
        ServiceSchedule s = schedule(Frequency.WEEKLY, LocalDate.of(2026, 10, 20), 4);
        Optional<LocalDate> next = MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, LocalDate.of(2026, 10, 19));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 20)), next);
    }

    @Test
    void weekly_advancesToNextWeekWhenFromDateIsAfterSession() {
        ServiceSchedule s = schedule(Frequency.WEEKLY, LocalDate.of(2026, 10, 20), 4);
        Optional<LocalDate> next = MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, LocalDate.of(2026, 10, 21));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 27)), next);
    }

    @Test
    void biweekly_respectsWeekParityFromStartDate() {
        ServiceSchedule s = schedule(Frequency.BIWEEKLY, LocalDate.of(2026, 10, 20), 3);
        // 20 oct → 3 nov (2 semanas después), no 27 oct
        assertEquals(Optional.of(LocalDate.of(2026, 11, 3)),
            MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, LocalDate.of(2026, 10, 21)));
    }

    @Test
    void occurrencesRespectNumberOfSessions() {
        // 1 sesión única: después de esa fecha no hay más ocurrencias
        ServiceSchedule s = schedule(Frequency.WEEKLY, LocalDate.of(2026, 10, 20), 1);
        assertTrue(MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, LocalDate.of(2026, 10, 21)).isEmpty());
    }

    @Test
    void scheduledStart_usesScheduleStartTimeInUtc() {
        ServiceSchedule s = schedule(Frequency.WEEKLY, LocalDate.of(2026, 10, 20), 1);
        var start = MeetingScheduleResolver.scheduledStartAt(s, LocalDate.of(2026, 10, 20));
        assertEquals("2026-10-20T15:00Z", start.toString());
    }

    @Test
    void startDayMismatchWithStartDate_isCallerResponsibility() {
        // el resolver asume la invariante del dominio (startDay == weekday(startDate))
        ServiceSchedule s = new ServiceSchedule(UUID.randomUUID(), UUID.randomUUID(), DayOfWeek.FRIDAY,
            60, Frequency.WEEKLY, 2, LocalDate.of(2026, 10, 20), LocalTime.of(15, 0), LocalTime.of(16, 0));
        assertEquals(Optional.of(LocalDate.of(2026, 10, 23)),
            MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, LocalDate.of(2026, 10, 20)));
    }
}
