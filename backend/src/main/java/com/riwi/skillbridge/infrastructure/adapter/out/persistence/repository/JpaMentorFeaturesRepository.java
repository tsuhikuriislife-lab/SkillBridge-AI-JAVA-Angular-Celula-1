package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorFeaturesEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface JpaMentorFeaturesRepository extends Repository<MentorFeaturesEntity, UUID> {
    Optional<MentorFeaturesEntity> findById(UUID mentorId);
}
