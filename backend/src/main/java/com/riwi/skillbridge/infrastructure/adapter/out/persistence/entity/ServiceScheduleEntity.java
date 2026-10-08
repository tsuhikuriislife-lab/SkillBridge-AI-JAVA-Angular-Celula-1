package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.Frequency;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "service_schedules")
public class ServiceScheduleEntity {
    @Id
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_day", nullable = false, length = 20)
    private DayOfWeek startDay;

    @Column(name = "session_duration", nullable = false)
    private int sessionDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Frequency frequency;

    @Column(name = "number_of_sessions", nullable = false)
    private int numberOfSessions;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    protected ServiceScheduleEntity() {}

    public ServiceScheduleEntity(UUID id, UUID serviceId, DayOfWeek startDay, int sessionDuration,
                                 Frequency frequency, int numberOfSessions, LocalDate startDate) {
        this.id = id; this.serviceId = serviceId; this.startDay = startDay;
        this.sessionDuration = sessionDuration; this.frequency = frequency;
        this.numberOfSessions = numberOfSessions; this.startDate = startDate;
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public DayOfWeek getStartDay() { return startDay; }
    public int getSessionDuration() { return sessionDuration; }
    public Frequency getFrequency() { return frequency; }
    public int getNumberOfSessions() { return numberOfSessions; }
    public LocalDate getStartDate() { return startDate; }
}
