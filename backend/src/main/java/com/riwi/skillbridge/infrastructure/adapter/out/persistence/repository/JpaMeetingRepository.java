package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.enums.MeetingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaMeetingRepository extends JpaRepository<MeetingEntity, UUID> {

    List<MeetingEntity> findByServiceId(UUID serviceId);

    List<MeetingEntity> findByStatus(MeetingStatus status);

    Optional<MeetingEntity> findByServiceIdAndScheduleIdAndMeetingDate(UUID serviceId, UUID scheduleId, LocalDate meetingDate);
}
