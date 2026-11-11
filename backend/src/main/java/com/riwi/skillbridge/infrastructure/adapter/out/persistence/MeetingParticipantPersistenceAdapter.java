package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.MeetingParticipantPort;
import com.riwi.skillbridge.domain.model.MeetingParticipant;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MeetingParticipantEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMeetingParticipantRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class MeetingParticipantPersistenceAdapter implements MeetingParticipantPort {

    private final JpaMeetingParticipantRepository repository;

    public MeetingParticipantPersistenceAdapter(JpaMeetingParticipantRepository repository) {
        this.repository = repository;
    }

    @Override
    public MeetingParticipant save(MeetingParticipant participant) {
        return toDomain(repository.save(toEntity(participant)));
    }

    @Override
    public Optional<MeetingParticipant> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<MeetingParticipant> findByMeetingIdAndUserId(UUID meetingId, UUID userId) {
        return repository.findFirstByMeetingIdAndUserIdOrderByCreatedAtDesc(meetingId, userId).map(this::toDomain);
    }

    @Override
    public List<MeetingParticipant> findByMeetingId(UUID meetingId) {
        return repository.findByMeetingId(meetingId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<MeetingParticipant> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    private MeetingParticipantEntity toEntity(MeetingParticipant p) {
        return new MeetingParticipantEntity(p.id(), p.meetingId(), p.userId(), p.role(), p.status(),
            p.joinedAt(), p.leftAt(), p.denialReason(), p.createdAt(), p.updatedAt());
    }

    private MeetingParticipant toDomain(MeetingParticipantEntity e) {
        return new MeetingParticipant(e.getId(), e.getMeetingId(), e.getUserId(), e.getRole(), e.getStatus(),
            e.getJoinedAt(), e.getLeftAt(), e.getDenialReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
