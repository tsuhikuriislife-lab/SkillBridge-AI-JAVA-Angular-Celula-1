package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.MeetingEventType;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_history")
public class MeetingHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private MeetingEventType eventType;

    @Column(length = 30)
    private String result;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MeetingHistoryEntity() {}

    public MeetingHistoryEntity(UUID id, UUID meetingId, UUID userId, MeetingEventType eventType,
                                String result, String reason, String metadata, OffsetDateTime createdAt) {
        this.id = id; this.meetingId = meetingId; this.userId = userId; this.eventType = eventType;
        this.result = result; this.reason = reason; this.metadata = metadata; this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMeetingId() { return meetingId; }
    public UUID getUserId() { return userId; }
    public MeetingEventType getEventType() { return eventType; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public String getMetadata() { return metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
