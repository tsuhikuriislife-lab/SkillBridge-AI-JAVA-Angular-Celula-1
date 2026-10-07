package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "offerings")
public class OfferingEntity {
    @Id
    private UUID id;
    private String title;
    private String description;
    private String category;
    private BigDecimal price;
    private boolean active;
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "provider_id")
    private UUID providerId;
    @Column(name = "start_time")
    private LocalTime startTime;
    @Column(name = "end_time")
    private LocalTime endTime;
    @Column(name = "end_day")
    private String endDay;
    @Column(name = "photo_url")
    private String photoUrl;

    protected OfferingEntity() {}

    public OfferingEntity(UUID id, String title, String description, String category, BigDecimal price, boolean active, UUID providerId, LocalTime startTime, LocalTime endTime, String endDay, String photoUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.price = price;
        this.active = active;
        this.providerId = providerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.endDay = endDay;
        this.photoUrl = photoUrl;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID providerId) { this.providerId = providerId; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getEndDay() { return endDay; }
    public void setEndDay(String endDay) { this.endDay = endDay; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
