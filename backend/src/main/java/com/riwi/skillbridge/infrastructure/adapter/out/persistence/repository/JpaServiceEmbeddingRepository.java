package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceEmbeddingEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface JpaServiceEmbeddingRepository extends Repository<ServiceEmbeddingEntity, UUID> {
    Optional<ServiceEmbeddingEntity> findById(UUID serviceId);
}
