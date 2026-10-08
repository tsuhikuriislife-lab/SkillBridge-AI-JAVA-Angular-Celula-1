package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.OfferingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> {
    List<OfferingEntity> findByStatusOrderByNameAsc(OfferingStatus status);
    Page<OfferingEntity> findByCreatedBy(UUID createdBy, Pageable pageable);
    boolean existsByCode(String code);
}