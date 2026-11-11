package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.MeetingParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingParticipantPort {

    MeetingParticipant save(MeetingParticipant participant);

    Optional<MeetingParticipant> findById(UUID id);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

    List<MeetingParticipant> findByMeetingId(UUID meetingId);

    List<MeetingParticipant> findByUserId(UUID userId);
}
