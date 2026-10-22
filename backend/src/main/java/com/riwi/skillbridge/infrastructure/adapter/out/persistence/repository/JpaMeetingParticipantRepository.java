package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MeetingParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaMeetingParticipantRepository extends JpaRepository<MeetingParticipantEntity, UUID> {

    Optional<MeetingParticipantEntity> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

    List<MeetingParticipantEntity> findByMeetingId(UUID meetingId);

    List<MeetingParticipantEntity> findByUserId(UUID userId);
}
