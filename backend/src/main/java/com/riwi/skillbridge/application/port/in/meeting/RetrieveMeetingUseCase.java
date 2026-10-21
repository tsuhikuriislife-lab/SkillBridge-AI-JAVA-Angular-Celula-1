package com.riwi.skillbridge.application.port.in.meeting;

import com.riwi.skillbridge.domain.model.Meeting;
import com.riwi.skillbridge.domain.model.MeetingHistoryEntry;
import com.riwi.skillbridge.domain.model.MeetingParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrieveMeetingUseCase {

    Optional<Meeting> findById(UUID meetingId);

    /** Reuniones de un servicio, visibles para el proveedor o para inscritos activos. */
    List<Meeting> findByService(UUID userId, UUID serviceId);

    List<MeetingParticipant> findParticipants(UUID userId, UUID meetingId);

    List<MeetingHistoryEntry> findHistory(UUID userId, UUID meetingId);
}
