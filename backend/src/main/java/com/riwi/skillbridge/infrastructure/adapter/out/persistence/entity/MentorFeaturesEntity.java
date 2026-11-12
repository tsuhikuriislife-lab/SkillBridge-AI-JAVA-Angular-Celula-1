package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Table(name = "mentor_features")
public class MentorFeaturesEntity {
    @Id
    @Column(name = "mentor_id")
    private UUID mentorId;

    @Column(name = "compatibility_score", nullable = false)
    private float compatibility;

    @Column(name = "expertise_score", nullable = false)
    private float expertise;

    @Column(name = "availability_score", nullable = false)
    private float availability;

    @Column(name = "popularity_score", nullable = false)
    private float popularity;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    protected MentorFeaturesEntity() {
    }

    public UUID getMentorId() {
        return mentorId;
    }

    public float getCompatibility() {
        return compatibility;
    }

    public float getExpertise() {
        return expertise;
    }

    public float getAvailability() {
        return availability;
    }

    public float getPopularity() {
        return popularity;
    }

    public UUID getBatchId() {
        return batchId;
    }
}
