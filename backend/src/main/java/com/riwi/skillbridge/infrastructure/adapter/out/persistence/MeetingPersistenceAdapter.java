package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.MeetingPort;
import com.riwi.skillbridge.domain.enums.MeetingStatus;
import com.riwi.skillbridge.domain.model.Meeting;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MeetingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMeetingRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class MeetingPersistenceAdapter implements MeetingPort {

    private final JpaMeetingRepository repository;

    public MeetingPersistenceAdapter(JpaMeetingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Meeting save(Meeting meeting) {
        return toDomain(repository.save(toEntity(meeting)));
    }

    @Override
    public Optional<Meeting> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Meeting> findByServiceId(UUID serviceId) {
        return repository.findByServiceId(serviceId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Meeting> findByStatus(MeetingStatus status) {
        return repository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Meeting> findByServiceIdAndScheduleIdAndMeetingDate(UUID serviceId, UUID scheduleId, LocalDate meetingDate) {
        return repository.findByServiceIdAndScheduleIdAndMeetingDate(serviceId, scheduleId, meetingDate)
            .map(this::toDomain);
    }

    private MeetingEntity toEntity(Meeting m) {
        return new MeetingEntity(m.id(), m.serviceId(), m.scheduleId(), m.meetingDate(), m.startTime(),
            m.durationMinutes(), m.status(), m.livekitRoom(), m.roomCreated(), m.scheduledStart(),
            m.actualStart(), m.actualEnd(), m.maxEnd(), m.extensionMinutes(), m.extensionUsed(),
            m.cancellationReason(), m.cancelledBy(), m.createdAt(), m.updatedAt());
    }

    private Meeting toDomain(MeetingEntity e) {
        return new Meeting(e.getId(), e.getServiceId(), e.getScheduleId(), e.getMeetingDate(), e.getStartTime(),
            e.getDurationMinutes(), e.getStatus(), e.getLivekitRoom(), e.isRoomCreated(), e.getScheduledStart(),
            e.getActualStart(), e.getActualEnd(), e.getMaxEnd(), e.getExtensionMinutes(), e.isExtensionUsed(),
            e.getCancellationReason(), e.getCancelledBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
