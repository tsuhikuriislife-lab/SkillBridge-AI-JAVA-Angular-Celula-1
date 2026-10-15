package com.riwi.skillbridge.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminActivity(
        UUID id,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String message,
        OffsetDateTime createdAt,
        String userId,
        String userEmail,
        String previousRole,
        String newRole
) {
    public AdminActivity(UUID id, UUID actorId, String action, String targetType,
                         UUID targetId, String message, OffsetDateTime createdAt) {
        this(id, actorId, action, targetType, targetId, message, createdAt, null, null, null, null);
    }
}

