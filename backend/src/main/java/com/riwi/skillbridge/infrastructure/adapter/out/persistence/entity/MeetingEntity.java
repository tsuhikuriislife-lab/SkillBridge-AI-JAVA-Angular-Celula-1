package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.MeetingStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class MeetingEntity {
    @Id
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetingStatus status;

    @Column(name = "livekit_room", nullable = false, unique = true, length = 120)
    private String livekitRoom;

    @Column(name = "room_created", nullable = false)
    private boolean roomCreated;

    @Column(name = "scheduled_start", nullable = false)
    private OffsetDateTime scheduledStart;

    @Column(name = "actual_start")
    private OffsetDateTime actualStart;

    @Column(name = "actual_end")
    private OffsetDateTime actualEnd;

    @Column(name = "max_end")
    private OffsetDateTime maxEnd;

    @Column(name = "extension_minutes", nullable = false)
    private int extensionMinutes;

    @Column(name = "extension_used", nullable = false)
    private boolean extensionUsed;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MeetingEntity() {}

    public MeetingEntity(UUID id, UUID serviceId, UUID scheduleId, LocalDate meetingDate,
                         LocalTime startTime, int durationMinutes, MeetingStatus status, String livekitRoom,
                         boolean roomCreated, OffsetDateTime scheduledStart, OffsetDateTime actualStart,
                         OffsetDateTime actualEnd, OffsetDateTime maxEnd, int extensionMinutes, boolean extensionUsed,
                         String cancellationReason, UUID cancelledBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id; this.serviceId = serviceId; this.scheduleId = scheduleId;
        this.meetingDate = meetingDate; this.startTime = startTime; this.durationMinutes = durationMinutes;
        this.status = status; this.livekitRoom = livekitRoom; this.roomCreated = roomCreated;
        this.scheduledStart = scheduledStart; this.actualStart = actualStart; this.actualEnd = actualEnd;
        this.maxEnd = maxEnd; this.extensionMinutes = extensionMinutes; this.extensionUsed = extensionUsed;
        this.cancellationReason = cancellationReason; this.cancelledBy = cancelledBy;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public UUID getScheduleId() { return scheduleId; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public LocalTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public MeetingStatus getStatus() { return status; }
    public String getLivekitRoom() { return livekitRoom; }
    public boolean isRoomCreated() { return roomCreated; }
    public OffsetDateTime getScheduledStart() { return scheduledStart; }
    public OffsetDateTime getActualStart() { return actualStart; }
    public OffsetDateTime getActualEnd() { return actualEnd; }
    public OffsetDateTime getMaxEnd() { return maxEnd; }
    public int getExtensionMinutes() { return extensionMinutes; }
    public boolean isExtensionUsed() { return extensionUsed; }
    public String getCancellationReason() { return cancellationReason; }
    public UUID getCancelledBy() { return cancelledBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
