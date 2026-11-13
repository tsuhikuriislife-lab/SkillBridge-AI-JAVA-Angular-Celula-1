package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Table(name = "service_embeddings")
public class ServiceEmbeddingEntity {
    @Id
    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "embedding", nullable = false, columnDefinition = "bytea")
    private byte[] embedding;

    @Column(name = "dimension", nullable = false)
    private int dimension;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    protected ServiceEmbeddingEntity() {
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public byte[] getEmbedding() {
        return embedding.clone();
    }

    public int getDimension() {
        return dimension;
    }

    public UUID getBatchId() {
        return batchId;
    }
}
