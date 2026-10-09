package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.Status;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@IdClass(UserPreferenceId.class)
public class UserPreferenceEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "preference_id")
    private UUID preferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    protected UserPreferenceEntity() {}

    public UserPreferenceEntity(UUID userId, UUID preferenceId, Status status) {
        this.userId = userId;
        this.preferenceId = preferenceId;
        this.status = status;
    }

    public UUID getUserId() { return userId; }
    public UUID getPreferenceId() { return preferenceId; }
    public Status getStatus() { return status; }
}
