package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.enums.MeetingStatus;
import com.riwi.skillbridge.domain.model.Meeting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingPort {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(UUID id);

    List<Meeting> findByServiceId(UUID serviceId);

    List<Meeting> findByStatus(MeetingStatus status);

    /** Reunión concreta de una sesión programada (servicio + horario + fecha). */
    Optional<Meeting> findByServiceIdAndScheduleIdAndMeetingDate(UUID serviceId, UUID scheduleId, LocalDate meetingDate);
}
