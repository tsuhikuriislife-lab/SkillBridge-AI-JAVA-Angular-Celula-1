package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.MeetingHistoryPort;
import com.riwi.skillbridge.domain.model.MeetingHistoryEntry;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MeetingHistoryEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMeetingHistoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MeetingHistoryPersistenceAdapter implements MeetingHistoryPort {

    private final JpaMeetingHistoryRepository repository;

    public MeetingHistoryPersistenceAdapter(JpaMeetingHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public MeetingHistoryEntry save(MeetingHistoryEntry entry) {
        MeetingHistoryEntity entity = new MeetingHistoryEntity(entry.id(), entry.meetingId(), entry.userId(),
            entry.eventType(), entry.result(), entry.reason(), entry.metadata(), entry.createdAt());
        MeetingHistoryEntity saved = repository.save(entity);
        return new MeetingHistoryEntry(saved.getId(), saved.getMeetingId(), saved.getUserId(),
            saved.getEventType(), saved.getResult(), saved.getReason(), saved.getMetadata(), saved.getCreatedAt());
    }

    @Override
    public List<MeetingHistoryEntry> findByMeetingId(UUID meetingId) {
        return repository.findByMeetingIdOrderByCreatedAtAsc(meetingId).stream()
            .map(e -> new MeetingHistoryEntry(e.getId(), e.getMeetingId(), e.getUserId(), e.getEventType(),
                e.getResult(), e.getReason(), e.getMetadata(), e.getCreatedAt()))
            .toList();
    }
}
