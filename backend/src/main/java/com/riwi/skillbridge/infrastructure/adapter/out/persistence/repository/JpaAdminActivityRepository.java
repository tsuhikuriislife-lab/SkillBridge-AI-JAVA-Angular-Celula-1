package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.AdminActivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaAdminActivityRepository extends JpaRepository<AdminActivityEntity, UUID> {
    Page<AdminActivityEntity> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);
}
