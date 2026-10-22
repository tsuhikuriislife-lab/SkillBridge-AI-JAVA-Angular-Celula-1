package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.domain.model.ServiceSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Cálculo centralizado de las ocurrencias de las sesiones de un horario
 * (Regla 3): a partir de startDate, startDay, startTime y la frecuencia
 * (WEEKLY / BIWEEKLY / MONTHLY), determina la fecha de cada sesión.
 * Reutiliza calculateEndDate de {@link ServiceScheduleService} para respetar
 * el número de sesiones contratadas.
 */
public final class MeetingScheduleResolver {

    private MeetingScheduleResolver() {}

    /**
     * Primera ocurrencia de la sesión en la fecha indicada o después de ella.
     * Vacío si el horario ya no tiene más sesiones vigentes.
     */
    public static Optional<LocalDate> nextOccurrenceOnOrAfter(ServiceSchedule schedule, LocalDate from) {
        LocalDate end = ServiceScheduleService.calculateEndDate(schedule);
        LocalDate candidate = from.isBefore(schedule.startDate()) ? schedule.startDate() : from;

        // Llevar el candidato al día de la semana de la sesión (máx. 6 días)
        DayOfWeek day = schedule.startDay();
        while (candidate.getDayOfWeek() != day && !candidate.isAfter(end)) {
            candidate = candidate.plusDays(1);
        }
        if (candidate.isAfter(end)) return Optional.empty();

        // Avanzar de semana en semana hasta coincidir con la frecuencia
        for (int i = 0; i < 104 && !candidate.isAfter(end); i++) { // tope: 2 años
            if (isOccurrence(schedule, candidate)) return Optional.of(candidate);
            candidate = candidate.plusDays(7);
        }
        return Optional.empty();
    }

    /** Inicio programado (UTC) de la sesión en la fecha de ocurrencia dada. */
    public static OffsetDateTime scheduledStartAt(ServiceSchedule schedule, LocalDate occurrenceDate) {
        return LocalDateTime.of(occurrenceDate, schedule.startTime()).atOffset(ZoneOffset.UTC);
    }

    private static boolean isOccurrence(ServiceSchedule schedule, LocalDate date) {
        return switch (schedule.frequency()) {
            case WEEKLY -> true; // ya coincide el día de la semana
            case BIWEEKLY -> ChronoUnit.WEEKS.between(schedule.startDate(), date) % 2 == 0;
            case MONTHLY -> weekOfMonth(schedule.startDate()) == weekOfMonth(date);
        };
    }

    /** Ordinal de la semana del mes: 1 = primera aparición de ese día de la semana. */
    private static int weekOfMonth(LocalDate date) {
        return (date.getDayOfMonth() - 1) / 7 + 1;
    }
}
