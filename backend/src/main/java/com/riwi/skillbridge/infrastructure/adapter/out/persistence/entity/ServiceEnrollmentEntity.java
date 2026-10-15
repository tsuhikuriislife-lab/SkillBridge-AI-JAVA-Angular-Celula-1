package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_services")
@IdClass(ServiceEnrollmentId.class)
public class ServiceEnrollmentEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "service_id")
    private UUID serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EnrollmentStatus status;

    @Column(name = "remaining_sessions", nullable = false)
    private int remainingSessions;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected ServiceEnrollmentEntity() {}

    public ServiceEnrollmentEntity(UUID userId, UUID serviceId, EnrollmentStatus status,
                                   int remainingSessions, LocalDate startDate, LocalDate endDate) {
        this.userId = userId; this.serviceId = serviceId; this.status = status;
        this.remainingSessions = remainingSessions;
        this.startDate = startDate; this.endDate = endDate;
    }

    public UUID getUserId() { return userId; }
    public UUID getServiceId() { return serviceId; }
    public EnrollmentStatus getStatus() { return status; }
    public int getRemainingSessions() { return remainingSessions; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
