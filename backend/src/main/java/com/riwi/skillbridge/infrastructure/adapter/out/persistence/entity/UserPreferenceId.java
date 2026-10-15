package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.UUID;

public class UserPreferenceId implements Serializable {

    private UUID userId;
    private UUID preferenceId;

    protected UserPreferenceId() {}

    public UserPreferenceId(UUID userId, UUID preferenceId) {
        this.userId = userId;
        this.preferenceId = preferenceId;
    }

    // equals y hashCode son OBLIGATORIOS para claves compuestas
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPreferenceId that)) return false;
        return userId.equals(that.userId) && preferenceId.equals(that.preferenceId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId, preferenceId);
    }
}
