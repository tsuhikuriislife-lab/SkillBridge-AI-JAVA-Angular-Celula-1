package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.OfferingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
public class OfferingEntity {
    @Id
    private UUID id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "learning_objectives", columnDefinition = "TEXT")
    private String learningObjectives;

    @Column(name = "prerequisites", columnDefinition = "TEXT")
    private String prerequisites;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OfferingStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected OfferingEntity() {}

    public OfferingEntity(UUID id, String code, String name, UUID categoryId, BigDecimal price,
                          String shortDescription, String detail, String learningObjectives,
                          String prerequisites, Integer capacity, OfferingStatus status, UUID createdBy) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.categoryId = categoryId;
        this.price = price;
        this.shortDescription = shortDescription;
        this.detail = detail;
        this.learningObjectives = learningObjectives;
        this.prerequisites = prerequisites;
        this.capacity = capacity;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getCategoryId() { return categoryId; }
    public BigDecimal getPrice() { return price; }
    public String getShortDescription() { return shortDescription; }
    public String getDetail() { return detail; }
    public String getLearningObjectives() { return learningObjectives; }
    public String getPrerequisites() { return prerequisites; }
    public Integer getCapacity() { return capacity; }
    public OfferingStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }

    public void update(String name, UUID categoryId, BigDecimal price, String shortDescription,
                       String detail, String learningObjectives, String prerequisites, Integer capacity) {
        this.name = name;
        this.categoryId = categoryId;
        this.price = price;
        this.shortDescription = shortDescription;
        this.detail = detail;
        this.learningObjectives = learningObjectives;
        this.prerequisites = prerequisites;
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(OfferingStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}