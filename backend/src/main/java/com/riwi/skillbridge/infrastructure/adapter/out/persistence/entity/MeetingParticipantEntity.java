package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.MeetingParticipantStatus;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipantEntity {
    @Id
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetingParticipantStatus status;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "denial_reason", columnDefinition = "TEXT")
    private String denialReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MeetingParticipantEntity() {}

    public MeetingParticipantEntity(UUID id, UUID meetingId, UUID userId, MeetingRole role,
                                    MeetingParticipantStatus status, OffsetDateTime joinedAt, OffsetDateTime leftAt,
                                    String denialReason, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id; this.meetingId = meetingId; this.userId = userId; this.role = role;
        this.status = status; this.joinedAt = joinedAt; this.leftAt = leftAt;
        this.denialReason = denialReason; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getMeetingId() { return meetingId; }
    public UUID getUserId() { return userId; }
    public MeetingRole getRole() { return role; }
    public MeetingParticipantStatus getStatus() { return status; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public OffsetDateTime getLeftAt() { return leftAt; }
    public String getDenialReason() { return denialReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
