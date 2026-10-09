package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.enums.ServiceStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "services")
public class OfferingEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "learning_objectives", columnDefinition = "TEXT")
    private String learningObjectives;

    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    private Integer capacity;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OfferingEntity() {}

    public OfferingEntity(UUID id, String name, UUID categoryId, BigDecimal price, String detail,
            String shortDescription, String learningObjectives, String prerequisites,
            Integer capacity, String code, ServiceStatus status, UUID createdBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id; this.name = name; this.categoryId = categoryId; this.price = price;
        this.detail = detail; this.shortDescription = shortDescription;
        this.learningObjectives = learningObjectives; this.prerequisites = prerequisites;
        this.capacity = capacity; this.code = code; this.status = status;
        this.createdBy = createdBy; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public UUID getCategoryId() { return categoryId; }
    public BigDecimal getPrice() { return price; }
    public String getDetail() { return detail; }
    public String getShortDescription() { return shortDescription; }
    public String getLearningObjectives() { return learningObjectives; }
    public String getPrerequisites() { return prerequisites; }
    public Integer getCapacity() { return capacity; }
    public String getCode() { return code; }
    public ServiceStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
