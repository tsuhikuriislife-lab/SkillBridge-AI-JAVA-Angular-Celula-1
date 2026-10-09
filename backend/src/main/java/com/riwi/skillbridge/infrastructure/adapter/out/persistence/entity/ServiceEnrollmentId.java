package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ServiceEnrollmentId implements Serializable {

    private UUID userId;
    private UUID serviceId;

    protected ServiceEnrollmentId() {}

    public ServiceEnrollmentId(UUID userId, UUID serviceId) {
        this.userId = userId;
        this.serviceId = serviceId;
    }

    // equals y hashCode son OBLIGATORIOS para claves compuestas en JPA
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceEnrollmentId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, serviceId); }
}
